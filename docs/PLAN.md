# Rencana berikutnya

Dicatat 17 Oktober 2026. Rilis terakhir: **1.0.13**.

## 1. Rapikan tab Tunnel

Masalah sekarang: semua kartu bertumpuk (banner bantuan, tunnel, alamat tunnel, domain Cloudflare, log), jadi panjang dan kurang jelas mana yang penting.

- [ ] Kartu status di paling atas: status tunnel, alamat utama besar dengan tombol Salin, tombol Start/Stop.
- [ ] Setelah secret key tersimpan, kolom secret disembunyikan di balik tombol **Ganti key** (setup tidak terus tampil).
- [ ] Sakelar (mulai bersama server, nyala saat app dibuka) dikelompokkan dalam satu bagian Pengaturan.
- [ ] Daftar semua tunnel dan notice playit jadi bagian yang bisa dilipat.
- [ ] Domain Cloudflare: tampilan ringkas saat DNS sudah benar (cukup status + domain), detail record SRV dan token di bagian yang bisa dibuka.
- [ ] Log tunnel dilipat secara default.
- [ ] Di PC: dua kolom (kiri tunnel + domain, kanan alamat + log), tinggi kartu sejajar seperti Beranda.

## 2. Rapikan Backup world (tab Dunia)

- [ ] Baris backup bergaya seperti Kelola mod: ikon arsip, nama, ukuran dan tanggal yang mudah dibaca, tanda **Terbaru**.
- [ ] Aksi pakai ikon (Unduh, Restore, Hapus) supaya tidak berjejer di layar HP; Restore dan Hapus tetap minta konfirmasi.
- [ ] Toolbar di atas: **Backup sekarang** dan **Upload world .zip**, plus info ukuran world dan jumlah backup.
- [ ] Progress saat backup/restore berjalan (bukan cuma toast).
- [ ] **Hapus world** dipindah ke bagian "Zona bahaya" terpisah di bawah.
- [ ] Ide: backup otomatis terjadwal (misal tiap 6 jam, simpan N terakhir).

## 3. File world di tab Dunia (baru)

Kartu **File world** untuk menjelajah isi folder server dari panel.

- [ ] Backend: `GET /api/files/list?path=`, `GET /api/files/download?path=`, `PUT /api/files/upload?path=`, `POST /api/files/delete`, `POST /api/files/mkdir`. Semua path dikunci di dalam folder server (tolak `..`, symlink keluar).
- [ ] UI: breadcrumb, daftar folder/file dengan ukuran dan tanggal, unduh file, upload ke folder yang dibuka, hapus (hanya saat server mati untuk isi world).
- [ ] Pintasan dimensi: Overworld (`world`), Nether (`world/DIM-1`), End (`world/DIM1`), plus total ukuran tiap dimensi.
- [ ] Editor teks untuk file kecil (`.json`, `.toml`, `.properties`, `.txt`, `.cfg`), terutama folder `config/`. Ini sekaligus jadi tempat ubah config mod (misal vein miner supaya tidak ikut ke tanah).
- [ ] Hapus satu dimensi saja (reset Nether/End) dengan konfirmasi.

## Catatan

- Setiap fitur dites dulu dengan panel desktop + server palsu, screenshot HP dan PC, lalu push ke `main` untuk build.
- Opsi "mode root" (jaga proses server dari LMK, governor CPU) masih tawaran, belum dikerjakan.
