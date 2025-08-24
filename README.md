# AgriShop (Android)

AgriShop is an Android app for small businesses to manage products, customers, invoices, and simple reports. Built with Kotlin, Jetpack components, Room, and MVVM.

## Features

- Manage products and customers
- Create invoices and view invoice history
- Export invoices to PDF (see `app/src/main/java/com/fertipos/agroshop/util/InvoicePdfGenerator.kt`)
- Basic reports export (see `app/src/main/java/com/fertipos/agroshop/util/ReportExporter.kt`)
- Local persistence via Room (`app/src/main/java/com/fertipos/agroshop/data/local/`)
- Dependency injection modules in `app/src/main/java/com/fertipos/agroshop/di/`

## Tech Stack

- Kotlin, Coroutines
- Jetpack: Room, ViewModel, (Compose-based UI screens under `app/src/main/java/com/fertipos/agroshop/ui/`)
- DI (Hilt-style module structure under `di/`)
- Gradle Kotlin DSL

## Project Structure

- `app/src/main/java/com/fertipos/agroshop/` — App code
  - `data/local` — Room entities, DAO, database
  - `ui/` — Screens, view models, components
  - `util/` — PDF/report utilities
  - `di/` — Dependency injection modules
- `app/src/main/res/` — Resources
- `app/schemas/` — Room schema files

## Build and Run

1. Open the project in Android Studio (Giraffe+ recommended).
2. Use the included Gradle Wrapper (`gradlew`) to build:
   - Windows: `gradlew.bat assembleDebug`
   - macOS/Linux: `./gradlew assembleDebug`
3. Run the app on an emulator or a connected device from Android Studio.

## PDF Export & FileProvider

The app uses a `FileProvider` to share generated PDFs.
- Paths are configured at `app/src/main/res/xml/file_paths.xml`.
- Manifest includes the provider entry in `AndroidManifest.xml`.

## Signing & Secrets

- Debug builds use the default debug keystore.
- Release signing files and properties (e.g., `keystore.properties`, `*.jks`) are intentionally excluded via `.gitignore`.
- Do not commit signing keys or any credentials.

## Development Notes

- Min/target SDK and dependencies are configured in `build.gradle` files.
- If you see line-ending warnings on Windows, you can run:
  ```bash
  git config core.autocrlf true
  ```

## License

Add your preferred license (e.g., MIT, Apache-2.0). If you want, I can add a `LICENSE` file.
