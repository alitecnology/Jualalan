KELASLIVE ADMIN — STARTER SERVER + ANDROID
=========================================

KEPUTUSAN YANG SUDAH DITERAPKAN
- Aplikasi Admin: Android saja.
- Tema default Admin: Dark Mode. Pemilik dapat memilih tema lain melalui menu Pengaturan Tema.
- Akun owner dibuat satu kali melalui konfigurasi server (tanpa pendaftaran publik).
- Pembayaran otomatis: Midtrans Snap; sandbox sebagai default untuk pengujian.
- Harga default: 1-6 hari, 1 minggu, dan 1 bulan (30 hari).
- Harga dihitung server-side; aplikasi tidak boleh mengirim nominal yang dipercaya server.
- Aktivasi hanya setelah webhook pembayaran tervalidasi.

PENTING SEBELUM PRODUKSI
1. Jangan pernah memasukkan Midtrans Server Key ke APK atau source code publik.
2. Jangan aktifkan production sebelum uji sandbox selesai.
3. Pembuatan akun merchant dan verifikasi identitas/payment provider harus ditangani orang dewasa/wali yang berwenang bila pemilik proyek belum memenuhi syarat usia/ketentuan provider. Jangan memalsukan usia atau data identitas.
4. Biaya gateway dapat mengurangi pendapatan, terutama untuk paket Rp1.000-Rp6.000. Harga publik Midtrans menampilkan biaya transfer bank/VA Rp4.000/transaksi dan QRIS 0,7% pada daftar umum; Midtrans juga menyatakan tarif berbeda berlaku untuk perusahaan gaming/produk digital. Konfirmasikan tarif yang benar untuk layanan KelasLive sebelum menetapkan metode pembayaran.
5. Starter ini belum di-deploy, belum terhubung ke akun Midtrans/Google Apps Script, dan APK belum dibangun atau diuji di perangkat fisik.

A. PASANG SERVER
1. Buka proyek Apps Script yang sama dengan GuruGate.gs.
2. Tambahkan file baru bernama KelasLiveAdminServer.gs dan salin isi server/KelasLiveAdminServer.gs.
3. Terapkan tambahan pada INTEGRASI-Gurugate.txt. Jangan mengubah fungsi lama selain menambahkan dua blok integrasi tersebut.
4. Jalankan klaInitDatabase() sekali dari editor Apps Script.
5. Di Project Settings > Script Properties, tambahkan sementara:
   KLA_SETUP_OWNER_EMAIL = email pemilik
   KLA_SETUP_OWNER_PASSWORD = sandi unik minimal 14 karakter
   Lalu jalankan klaConfigureOwnerFromSetup(). Kedua nilai setup akan dihapus otomatis setelah berhasil. Jangan bagikan sandi melalui chat.
6. Untuk sandbox, tambahkan sementara KLA_SETUP_MIDTRANS_SERVER_KEY dan KLA_SETUP_MIDTRANS_ENV=sandbox pada Script Properties, lalu jalankan klaConfigureMidtransFromSetup(). Nilai setup akan dihapus otomatis. Server Key sebenarnya tetap tersimpan sebagai Script Property server.
7. Deploy Web App sebagai akun pemilik script. API admin menggunakan login/session internal; endpoint webhook harus dapat menerima POST publik.
8. Atur Payment Notification URL pada dashboard Midtrans ke:
   https://script.google.com/macros/s/ID_DEPLOYMENT/exec?a=midtrans_notification
9. Setelah perubahan kode, deploy New version.

B. AKSI API
POST JSON ke URL /exec:
- {"a":"kla_owner_login","email":"...","password":"..."}
- {"a":"kla_dashboard","session":"..."}
- {"a":"kla_prices","session":"..."}
- {"a":"kla_set_price","session":"...","package_id":"7d","price_idr":6000}
- {"a":"kla_orders","session":"..."}
- {"a":"kla_devices","session":"..."}
- {"a":"kla_device_status","session":"...","device_id":"...","status":"blocked"}
- {"a":"kla_checkout","device_id":"DEVICE_ID_MIN_8_CHAR","package_id":"7d"}

GET /exec?a=kla_device_check&device_id=... dipakai nanti oleh aplikasi User untuk memeriksa masa aktif.
Webhook Midtrans menggunakan POST URL dengan parameter a=midtrans_notification. Jangan panggil endpoint webhook dari aplikasi.

C. ANDROID
Folder android berisi proyek Android Studio awal (min Android 9/API 28). Ganti apiUrl di MainActivity.kt dengan URL deployment Web App setelah server aktif. Source Android ini starter UI untuk login owner, dashboard, pengaturan harga, daftar perangkat, dan riwayat transaksi. APK belum dibangun dan belum diuji pada perangkat fisik.


D. PENGATURAN TEMA APLIKASI ADMIN
- Menu PENGATURAN TEMA tersedia dari layar login dan dashboard.
- Tema bawaan: Dark Mode, Light Mode, Purple Mode, dan Premium Mode (hitam-emas).
- Custom Theme menyediakan warna background, warna utama, tombol, teks, kartu, navigasi/menu,
  sudut kartu, ukuran teks, wallpaper dashboard, dan logo aplikasi.
- PREVIEW TEMA menampilkan contoh sebelum disimpan. SAVE THEME menyimpan pilihan.
- Tema disimpan lokal pada SharedPreferences Android dan tetap aktif setelah aplikasi dibuka ulang.
- Menu navigasi dalam aplikasi (Beranda, Harga, Perangkat, Transaksi, Tema) memakai warna
  navigasi/menu dari tema aktif; ini terpisah dari warna bilah navigasi sistem Android.
- Implementasi tema dipisah dalam ThemeManager.kt; endpoint server, login, database,
  harga, Midtrans, transaksi, dan integrasi GuruGate tidak diubah.
- Wallpaper dan logo dipilih dari penyimpanan perangkat melalui pemilih dokumen Android.
