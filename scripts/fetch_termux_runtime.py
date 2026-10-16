#!/usr/bin/env python3
"""
Builds app/src/main/assets/runtime/{jre.tar.gz,version} from Termux's aarch64 packages.

Termux packages are compiled for Android's bionic libc, so the bundled `java` runs natively on
the phone without Termux being installed. We download the JDK package plus its library
dependencies, flatten symlinks (APK assets cannot hold them), drop dev-only parts, swap in a
portable cacerts and pack everything as a GNU tar.gz that the app extracts on first launch.

Layout inside the archive:
  jvm/   JAVA_HOME (bin/java, lib/server/libjvm.so, lib/modules, ...)
  lib/   shared libraries from dependency packages (libandroid-shmem.so, libz.so.1, ...)
"""
import argparse
import bz2
import gzip
import lzma
import os
import shutil
import subprocess
import sys
import tarfile
import urllib.request

TERMUX_PREFIX = "/data/data/com.termux/files/usr"
DEFAULT_MIRRORS = [
    "https://packages-cf.termux.dev/apt/termux-main",
    "https://packages.termux.dev/apt/termux-main",
    "https://grimler.se/termux/termux-main",
]
ARCH = "aarch64"
# Provided by Android itself; never needs bundling.
SYSTEM_LIBS = {"libc.so", "libm.so", "libdl.so", "liblog.so", "libandroid.so", "libz.so", "libEGL.so",
               "libGLESv2.so", "libjnigraphics.so", "libOpenSLES.so", "libmediandk.so", "libvulkan.so"}
DROP_FROM_JVM = ["jmods", "include", "demo", "man", "sample", "lib/src.zip", "lib/ct.sym"]


def log(*a):
    print("[runtime]", *a, flush=True)


def http_get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "mchost-ci"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def fetch_index(mirrors):
    for m in mirrors:
        base = f"{m}/dists/stable/main/binary-{ARCH}/"
        for name, dec in (("Packages", lambda b: b), ("Packages.xz", lzma.decompress),
                          ("Packages.gz", gzip.decompress), ("Packages.bz2", bz2.decompress)):
            try:
                data = dec(http_get(base + name)).decode("utf-8", "replace")
                if "Package:" in data:
                    log("index", base + name)
                    return m, data
            except Exception as e:  # noqa: BLE001
                log("skip", base + name, type(e).__name__, e)
    sys.exit("Tidak bisa mengambil index paket Termux dari semua mirror")


def parse_index(text):
    pkgs, provides = {}, {}
    for block in text.split("\n\n"):
        fields, key = {}, None
        for line in block.splitlines():
            if line.startswith((" ", "\t")) and key:
                fields[key] += "\n" + line.strip()
            elif ":" in line:
                key, _, val = line.partition(":")
                key = key.strip()
                fields[key] = val.strip()
        name = fields.get("Package")
        if not name:
            continue
        pkgs[name] = fields
        for p in split_deps(fields.get("Provides", "")):
            provides.setdefault(p, name)
    return pkgs, provides


def split_deps(s):
    out = []
    for part in s.split(","):
        part = part.strip()
        if not part:
            continue
        alt = part.split("|")[0].strip()          # first alternative
        alt = alt.split("(")[0].strip()           # drop version constraint
        alt = alt.split(":")[0].strip()           # drop :any arch qualifier
        if alt:
            out.append(alt)
    return out


def resolve(root_pkgs, pkgs, provides, skip):
    order, seen, stack = [], set(), list(root_pkgs)
    while stack:
        name = stack.pop()
        name = name if name in pkgs else provides.get(name, name)
        if name in seen or name in skip:
            continue
        if name not in pkgs:
            log("WARNING dependency tidak ada di index:", name)
            seen.add(name)
            continue
        seen.add(name)
        order.append(name)
        f = pkgs[name]
        stack.extend(split_deps(f.get("Depends", "")) + split_deps(f.get("Pre-Depends", "")))
    return order


class Tree:
    """Merged extraction root with Termux-aware symlink resolution."""

    def __init__(self, root):
        self.root = root
        self.prefix = os.path.join(root, TERMUX_PREFIX.lstrip("/"))

    def real(self, path, depth=0):
        """Follow symlinks; absolute targets are mapped into the merged root."""
        if depth > 40:
            return None
        if not os.path.islink(path):
            return path if os.path.exists(path) else None
        target = os.readlink(path)
        if os.path.isabs(target):
            nxt = os.path.join(self.root, target.lstrip("/"))
        else:
            nxt = os.path.normpath(os.path.join(os.path.dirname(path), target))
        # resolve parents that may themselves be links
        parent = os.path.dirname(nxt)
        if parent != nxt and parent.startswith(self.root) and not os.path.exists(parent):
            rp = self.real(parent, depth + 1)
            if rp:
                nxt = os.path.join(rp, os.path.basename(nxt))
        return self.real(nxt, depth + 1)

    def copy_tree(self, src, dst):
        os.makedirs(dst, exist_ok=True)
        for name in sorted(os.listdir(src)):
            s, d = os.path.join(src, name), os.path.join(dst, name)
            r = self.real(s)
            if r is None:
                log("WARNING link rusak dilewati:", os.path.relpath(s, self.root), "->", os.readlink(s))
                continue
            if os.path.isdir(r):
                self.copy_tree(r, d)
            else:
                shutil.copy2(r, d)


def needed_libs(path):
    try:
        out = subprocess.run(["readelf", "-d", path], capture_output=True, text=True, check=False).stdout
    except FileNotFoundError:
        return []
    libs = []
    for line in out.splitlines():
        if "(NEEDED)" in line and "[" in line:
            libs.append(line.split("[", 1)[1].split("]", 1)[0])
    return libs


def is_elf(path):
    try:
        with open(path, "rb") as f:
            return f.read(4) == b"\x7fELF"
    except OSError:
        return False


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pkg", default="openjdk-17")
    ap.add_argument("--out", required=True, help="assets/runtime directory")
    ap.add_argument("--work", default="build/termux-runtime")
    ap.add_argument("--mirror", action="append", help="override mirror base URL(s)")
    ap.add_argument("--cacerts", default=None, help="portable cacerts file (default: $JAVA_HOME/lib/security/cacerts)")
    ap.add_argument("--skip", action="append", default=[], help="package names to never pull")
    a = ap.parse_args()

    mirror, text = fetch_index(a.mirror or DEFAULT_MIRRORS)
    pkgs, provides = parse_index(text)
    if a.pkg not in pkgs:
        cands = sorted(n for n in pkgs if n.startswith("openjdk"))
        sys.exit(f"Paket {a.pkg} tidak ada. Yang tersedia: {', '.join(cands)}")

    order = resolve([a.pkg], pkgs, provides, set(a.skip))
    log("paket:", ", ".join(f"{n} {pkgs[n].get('Version')}" for n in order))

    shutil.rmtree(a.work, ignore_errors=True)
    debs = os.path.join(a.work, "debs")
    root = os.path.join(a.work, "root")
    stage = os.path.join(a.work, "stage")
    os.makedirs(debs)
    os.makedirs(root)
    for n in order:
        f = pkgs[n]
        url = f"{mirror}/{f['Filename']}"
        dest = os.path.join(debs, os.path.basename(f["Filename"]))
        log("download", url)
        with open(dest, "wb") as fh:
            fh.write(http_get(url))
        subprocess.run(["dpkg-deb", "-x", dest, root], check=True)

    tree = Tree(root)
    jvm_parent = os.path.join(tree.prefix, "lib", "jvm")
    jvms = [d for d in sorted(os.listdir(jvm_parent)) if os.path.isdir(os.path.join(jvm_parent, d)) and not os.path.islink(os.path.join(jvm_parent, d))] if os.path.isdir(jvm_parent) else []
    if not jvms:
        sys.exit("lib/jvm tidak ditemukan di paket JDK")
    jvm_src = os.path.join(jvm_parent, jvms[0])
    log("JAVA_HOME sumber:", os.path.relpath(jvm_src, root))

    shutil.rmtree(stage, ignore_errors=True)
    tree.copy_tree(jvm_src, os.path.join(stage, "jvm"))
    for rel in DROP_FROM_JVM:
        p = os.path.join(stage, "jvm", rel)
        if os.path.isdir(p):
            shutil.rmtree(p)
        elif os.path.exists(p):
            os.remove(p)

    # dependency shared libraries (top level of $PREFIX/lib only)
    libdst = os.path.join(stage, "lib")
    os.makedirs(libdst, exist_ok=True)
    plib = os.path.join(tree.prefix, "lib")
    for name in sorted(os.listdir(plib)):
        if ".so" not in name:
            continue
        r = tree.real(os.path.join(plib, name))
        if r and os.path.isfile(r) and is_elf(r):
            shutil.copy2(r, os.path.join(libdst, name))

    # portable trust store (Termux's points into $PREFIX/etc)
    cacerts = a.cacerts or (os.path.join(os.environ["JAVA_HOME"], "lib", "security", "cacerts") if os.environ.get("JAVA_HOME") else None)
    sec = os.path.join(stage, "jvm", "lib", "security")
    if cacerts and os.path.isfile(cacerts):
        os.makedirs(sec, exist_ok=True)
        dst = os.path.join(sec, "cacerts")
        if os.path.lexists(dst):
            os.remove(dst)
        shutil.copy2(cacerts, dst)
        log("cacerts diganti dari", cacerts)
    else:
        log("WARNING cacerts host tidak ditemukan, pakai bawaan paket")

    java = os.path.join(stage, "jvm", "bin", "java")
    if not os.path.isfile(java):
        sys.exit("jvm/bin/java tidak ada setelah staging")

    # sanity: every NEEDED lib must exist somewhere we put on LD_LIBRARY_PATH
    have = set(os.listdir(libdst))
    for d in ("jvm/lib", "jvm/lib/server"):
        p = os.path.join(stage, d)
        if os.path.isdir(p):
            have |= set(os.listdir(p))
    missing = set()
    for dirpath, _, files in os.walk(stage):
        for fn in files:
            fp = os.path.join(dirpath, fn)
            if is_elf(fp):
                for lib in needed_libs(fp):
                    if lib not in have and lib not in SYSTEM_LIBS:
                        missing.add((lib, os.path.relpath(fp, stage)))
    for lib, who in sorted(missing):
        log(f"WARNING {who} butuh {lib} yang tidak dibundel")
    os.chmod(java, 0o755)

    os.makedirs(a.out, exist_ok=True)
    tgz = os.path.join(a.out, "jre.tar.gz")
    with tarfile.open(tgz, "w:gz", format=tarfile.GNU_FORMAT, compresslevel=9) as tf:
        for top in ("jvm", "lib"):
            tf.add(os.path.join(stage, top), arcname=top)
    version = f"{a.pkg} {pkgs[a.pkg].get('Version')}"
    with open(os.path.join(a.out, "version"), "w") as fh:
        fh.write(version + "\n")
    size = os.path.getsize(tgz) / 1024 / 1024
    log(f"selesai: {tgz} ({size:.1f} MB), versi {version}")


if __name__ == "__main__":
    main()
