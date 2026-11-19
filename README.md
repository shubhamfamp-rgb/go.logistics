# go.logistics

A calm, minimalist Android application built with Kotlin and Jetpack Compose for transport businesses, fleet owners, and roadline offices.

Live web preview & interactive showcase: `index.html` (GitHub Pages ready).

## Features

- **Real-Time Mobile Dashboard (`Overview`)**: Live freight revenue, diesel & toll expenses, net operating margin, smooth cashflow chart, and active 3-day early compliance alerts.
- **Excel Sheets & Live Sync (`Sheets`)**:
  - Multi-workbook transport ledgers synced with Cloud Firestore.
  - Inline row editor with live formula totals (`SUM(Freight)`, `SUM(Expense)`, `Net Margin`).
  - **Bill Photo to Sheet**: Extract dates, vehicle plates, LR/bill numbers, and amounts directly from uploaded invoice or fuel slip photos.
  - **CSV Import & Native `.xlsx` Export**: Open `.csv` ledgers or export standards-compliant OpenXML `.xlsx` workbooks for Microsoft Excel, Google Sheets, and Apple Numbers.
  - **External Excel / Sheet Connector**: Store and manage linked workbook URLs per register.
- **Garage, Vehicle Info Lookup & 3D Models (`Garage`)**:
  - **Plate Lookup**: Enter Indian registration numbers (e.g., `MH12PQ4821`, `MHXXA0000`) to decode state and RTO offices, check Parivahan portal status, open Vahan / e-Challan web portals, or import vehicle records from RC/challan/insurance photos.
  - **Interactive 3D Vehicle Models**: Touch-and-drag 360° 3D studio views for **LMV**, **Bike**, **Bus**, **Truck**, and **Long Vehicle** trailers.
  - **3-Day Early Reminders**: Monitors pending challans, RTC/RTO permits, and insurance renewal dates and alerts the owner starting 3 days before expiry.
- **PDF, Bill & Image Studio (`Studio`)**:
  - **PDF Editor**: Open agency statements or invoice PDFs, browse pages, place text edits or whiteout blocks, insert photos, and export updated PDFs.
  - **Bill + Photos Composer**: Create clean freight invoices or POD bills with your company letterhead and dispatch photos embedded inside the PDF.
  - **Image Quality Optimizer**: Enhance faded receipt clarity or compress JPEG quality and dimensions with live KB/MB size savings.
- **Transport Tools & Appearance (`Tools`)**:
  - Trip Diesel & Freight Profit Calculator
  - GTA Freight GST (5% RCM / 12% FCM) & E-Way Bill threshold helper
  - Axle & Payload capacity reference
  - System / Light / Dark appearance switcher & Office Letterhead preferences

## Tech Stack

- **UI**: Kotlin, Jetpack Compose, Material 3, Navigation Compose
- **Backend & Auth**: Firebase Firestore, Firebase Auth (Google Sign-In via Jetpack Credential Manager), Firebase App Check, Firebase AI Logic
- **Storage & Preferences**: Jetpack DataStore Preferences
- **Document & Graphics**: Android `PdfRenderer` / `PdfDocument`, pure-Kotlin OpenXML `.xlsx` ZIP writer, Compose 3D Canvas perspective renderer

## Building Locally

1. Clone the repository and open it in Android Studio (or build via Gradle CLI).
2. Ensure Firebase configuration (`app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`) is configured for your Firebase project.
3. Build & verify:

```bash
gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

## License

MIT License — see [LICENSE](LICENSE).
