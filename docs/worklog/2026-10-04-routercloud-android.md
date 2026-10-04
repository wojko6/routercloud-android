# RouterCloud Android — Worklog 2026-10-04

## Status

Pierwszy natywny klient Android dla RouterCloud został uruchomiony na fizycznym urządzeniu i połączony z działającym backendem RouterCloud.

Stan bieżący:

- Build Android: PASS
- Instalacja na fizycznym urządzeniu: PASS
- HTTPS: PASS
- RouterCloud Local CA v2: PASS
- Logowanie: PASS
- Sesja cookie: PASS
- Listowanie katalogów: PASS
- Nawigacja po katalogach: PASS
- Pobieranie plików: PASS
- Otwieranie plików przez Android: PASS
- Wewnętrzny podgląd tekstu: PASS
- Upload z aplikacji: PASS
- Ochrona przed przypadkowym nadpisaniem: PASS
- Android Share Target: PASS
- Share → wybór katalogu → upload: PASS
- Tworzenie katalogów: PASS
- API zmiany nazwy: PASS
- UI zmiany nazwy: PASS

---

## 1. Projekt Android

Nazwa aplikacji: RouterCloud

Package / namespace:

`com.wojko6.routercloud`

Technologie:

- Kotlin
- Jetpack Compose
- Gradle Kotlin DSL
- OkHttp

Lokalizacja projektu:

`~/Projekty/routercloud-android`

Konfiguracja SDK:

- compileSdk: 37
- targetSdk: 37
- minSdk: 24

Toolchain:

- Android Gradle Plugin: 9.4.1
- Kotlin: 2.2.10
- Compose BOM: 2026.02.01
- OkHttp: 5.5.0
- Android Studio: Rabbit 1 / 2026.2.1
- Android SDK: `$HOME/Android/Sdk`

---

## 2. Git

Repozytorium lokalne działa na gałęzi:

`main`

Pierwszy commit projektu:

`6791001 feat(android): bootstrap RouterCloud Compose app`

W kolejnych checkpointach dodano:

- konfigurację bezpieczeństwa sieciowego
- RouterCloud Local CA v2
- logowanie
- sesję
- listowanie katalogów
- nawigację katalogów
- pobieranie plików
- FileProvider
- wewnętrzny podgląd tekstu
- upload
- Android Share Target
- tworzenie katalogów
- warstwę sieciową zmiany nazwy

Zdalne repozytorium GitHub dla klienta Android nie zostało jeszcze skonfigurowane.

---

## 3. Backend RouterCloud

Klient Android korzysta z istniejącego backendu RouterCloud/Dufs.

Na obecnym etapie nie było potrzeby tworzenia osobnego API `/api/v1`.

### Logowanie

Endpoint:

`POST /__routercloud/login`

Content-Type:

`application/x-www-form-urlencoded`

Pola:

- `username`
- `password`

Poprawne logowanie:

`HTTP 204 No Content`

Backend ustawia cookie:

`__Host-routercloud_session`

Właściwości cookie:

- HttpOnly
- Secure
- SameSite=Strict
- Path=/
- Max-Age=43200

Cookie jest obecnie przechowywane tylko w pamięci procesu aplikacji.

Hasło nie jest trwale zapisywane.

Po poprawnym zalogowaniu hasło jest usuwane ze stanu UI.

---

## 4. Listowanie katalogów

Do pobierania zawartości katalogów używany jest istniejący interfejs JSON Dufs:

`GET /?json`

Dla podkatalogów:

`GET /katalog/?json`

Backend udostępnia m.in.:

- `paths`
- `storage`
- `allow_upload`
- `allow_move`
- `allow_delete`
- `routercloud_allow_delete`
- `allow_search`
- `allow_archive`

Weryfikacja kontraktu backendu:

    LOGIN_HTTP=204
    LIST_HTTP=200
    DIR_EXISTS=true
    PATHS=22
    ALLOW_UPLOAD=true
    ALLOW_MOVE=true
    ALLOW_DELETE=true
    STORAGE_PRESENT=true

---

## 5. PKI / TLS

Podczas przygotowywania klienta Android wykryto problem starego lokalnego CA RouterCloud.

Stary CA:

`RouterCloud Local CA`

posiadał:

`Basic Constraints: CA:TRUE`

ale brakowało poprawnego rozszerzenia Key Usage.

Ścisła walidacja OpenSSL zgłaszała:

`CA cert does not include key usage extension`

### RouterCloud Local CA v2

Utworzono nowe CA:

`RouterCloud Local CA v2`

Poprawne rozszerzenia:

- Basic Constraints: critical, CA:TRUE
- Key Usage: critical
- Certificate Sign
- CRL Sign

Wystawiono nowy certyfikat serwera:

`cloud.home.arpa`

SAN:

- DNS: `cloud.home.arpa`
- IP: `ROUTERCLOUD_LAN_IP`

Certyfikat posiada poprawne przeznaczenie TLS Server Authentication.

Końcowa weryfikacja:

    depth=1 CN=RouterCloud Local CA v2
    depth=0 CN=cloud.home.arpa
    Verification: OK
    Verify return code: 0 (ok)

Endpoint:

`https://cloud.home.arpa/__routercloud/login`

zwraca:

`HTTP 200`

Stare PKI oraz backup poprzedniego aktywnego TLS zostały zachowane jako możliwość rollbacku.

---

## 6. Android Network Security

Publiczny certyfikat RouterCloud Local CA v2 został dodany do aplikacji jako własny trust anchor.

Pliki:

`app/src/main/res/raw/routercloud_ca_v2.crt`

`app/src/main/res/xml/network_security_config.xml`

Założenia bezpieczeństwa:

- brak trust-all
- brak wyłączania TLS
- brak omijania hostname verification
- brak cleartext dla RouterCloud
- walidacja `cloud.home.arpa`
- poprawny łańcuch certyfikatów
- prywatny klucz CA nigdy nie trafia do aplikacji

---

## 7. Logowanie

Ekran logowania obsługuje:

- login RouterCloud
- skonfigurowany alias e-mail
- hasło

Flow:

    POST /__routercloud/login
             |
             v
       HTTP 204 + cookie
             |
             v
         GET /?json
             |
             v
       katalog główny

Test na fizycznym urządzeniu:

`PASS`

---

## 8. Nawigacja po katalogach

Aplikacja obsługuje:

- katalog główny
- otwieranie podkatalogów
- dynamiczne pobieranie `/<path>/?json`
- wyświetlanie bieżącej ścieżki
- przycisk `← Wstecz`
- systemowy Android Back

Test katalogu `nowy`:

`PASS`

---

## 9. Pobieranie plików

Pliki są pobierane po HTTPS z wykorzystaniem aktualnej sesji RouterCloud.

Cache:

`cache/routercloud-downloads/`

Plik nie jest automatycznie zapisywany do publicznego katalogu Downloads.

---

## 10. FileProvider

Do bezpiecznego przekazywania pobranych plików innym aplikacjom używany jest Android FileProvider.

Konfiguracja:

`app/src/main/res/xml/file_paths.xml`

Udostępniany jest wyłącznie katalog:

`routercloud-downloads/`

z prywatnego cache aplikacji.

Do aplikacji zewnętrznej przekazywany jest URI z:

`FLAG_GRANT_READ_URI_PERMISSION`

Test:

`PASS`

---

## 11. Wewnętrzny podgląd tekstu

Obsługiwane są m.in.:

- txt
- md
- log
- json
- xml
- yaml
- yml
- csv
- ini
- conf
- cfg
- properties
- sh
- kt
- java
- py
- js
- css
- html

Ekran podglądu umożliwia:

- przewijanie
- zaznaczanie tekstu
- cofnięcie
- użycie `Otwórz w…`

Limit podglądu:

`1 MiB`

Chroni to aplikację przed próbą załadowania bardzo dużych plików tekstowych bezpośrednio do UI.

Test:

`PASS`

---

## 12. Upload z aplikacji

W aplikacji działa:

`↑ Wyślij plik`

Plik wybierany jest przez Android Storage Access Framework:

`ActivityResultContracts.OpenDocument`

Dzięki temu aplikacja nie wymaga szerokich uprawnień do pamięci telefonu.

Transfer wykonywany jest strumieniowo metodą:

`HTTP PUT`

Po uploadzie bieżący katalog jest automatycznie odświeżany.

Test:

`PASS`

---

## 13. Ochrona przed przypadkowym nadpisaniem

Przed wysłaniem aplikacja sprawdza, czy w bieżącym katalogu istnieje już element o tej samej nazwie.

Domyślna polityka:

`NO IMPLICIT OVERWRITE`

Jeżeli nazwa już istnieje, operacja jest zatrzymywana i użytkownik otrzymuje komunikat.

Test:

`PASS`

---

## 14. Android Share Target

RouterCloud jest zarejestrowany jako odbiorca:

`android.intent.action.SEND`

MIME:

`*/*`

Plik można przekazać m.in. z:

- Galerii
- menedżera plików
- innych aplikacji Android

Flow:

    Galeria / Pliki / inna aplikacja
                 |
                 v
             Udostępnij
                 |
                 v
            RouterCloud
                 |
                 v
       wybór katalogu docelowego
                 |
                 v
           Wyślij tutaj
                 |
                 v
             HTTPS PUT

Test:

`PASS`

---

## 15. Tworzenie katalogów

Aplikacja obsługuje WebDAV:

`MKCOL`

Poprawna odpowiedź backendu:

`201 Created`

UI:

`＋ Katalog`

Walidacja blokuje m.in.:

- pustą nazwę
- `.`
- `..`
- `/`
- `\`

Po utworzeniu katalogu lista jest automatycznie odświeżana.

Istniejący element o tej samej nazwie nie jest zastępowany.

Test:

`PASS`

---

## 16. Zmiana nazwy

Warstwa sieciowa została zaimplementowana.

Używana metoda:

`MOVE`

Nagłówek:

`Destination`

Backend RouterCloud realizuje bezpieczną politykę zmiany nazwy:

- źródło i cel muszą znajdować się w tym samym katalogu
- operacja nie służy do przenoszenia elementów pomiędzy katalogami
- istniejący cel nie jest nadpisywany
- konflikt istniejącej nazwy jest zwracany jako błąd

Stan:

- Network API: PASS
- Build: PASS
- UI: PASS
- E2E na urządzeniu: PASS

---

## 17. Aktualny model bezpieczeństwa

Obecny klient Android:

- wymusza HTTPS
- korzysta z RouterCloud Local CA v2
- nie używa trust-all
- nie omija walidacji certyfikatu
- nie omija hostname verification
- nie przechowuje hasła w kodzie
- nie zapisuje hasła do pliku
- przechowuje sesję tylko w pamięci procesu
- używa FileProvider
- korzysta ze Storage Access Framework
- nie wymaga szerokiego dostępu do pamięci telefonu
- nie nadpisuje automatycznie istniejących plików

---

## 18. Macierz funkcjonalności

    [PASS] Build
    [PASS] Physical Android device
    [PASS] HTTPS / PKI v2
    [PASS] Login
    [PASS] Session cookie
    [PASS] Root listing
    [PASS] Directory navigation
    [PASS] Android Back
    [PASS] Download
    [PASS] FileProvider
    [PASS] Open in external application
    [PASS] Internal text preview
    [PASS] Upload
    [PASS] Duplicate upload protection
    [PASS] Android Share Target
    [PASS] Share → choose directory → upload
    [PASS] Create directory
    [PASS] Rename network API
    [PASS] Rename UI
    [PASS] Rename E2E
    [PASS] Delete UI
    [PASS] Delete E2E
    [PASS] Secure persistent session
    [PASS] Android Keystore
    [PASS] Fingerprint unlock
    [PASS] Fingerprint unlock E2E
    [PASS] Backend permissions respected in UI
    [PASS] Storage information
    [PASS] Metro storage tile
    [PASS] Metro file type icons
    [PASS] Windows Mobile inspired icon language

---

## 19. Następne kroki

Planowana kolejność:

1. UI zmiany nazwy pliku lub katalogu
2. test E2E metody MOVE
3. usuwanie pliku lub katalogu z potwierdzeniem
4. respektowanie flag uprawnień backendu w UI
5. informacje o zajętości przestrzeni
6. lepsze ikony plików
7. dalszy Metro UI
8. wyszukiwarka
9. favorites
10. recent files
11. obsługa scenariuszy LAN / Tailscale
12. bezpieczne trwałe przechowywanie sesji
13. Android Keystore
14. background upload / WorkManager
15. progress dużych transferów
16. retry / resume
17. podgląd zdjęć
18. galeria zdjęć i wideo
19. opcjonalny backup aparatu
20. biometric unlock

---

## 20. Backend WWW a Android

Na potrzeby obecnego MVP nie było konieczne tworzenie `/api/v1`.

Klient korzysta z już działających mechanizmów RouterCloud/Dufs:

- session login
- JSON directory listing
- GET
- PUT
- MKCOL
- MOVE
- DELETE w kolejnym etapie

Nowe API powinno zostać dodane dopiero wtedy, gdy klient będzie wymagał funkcji lub stabilnego kontraktu, którego obecny backend nie zapewnia.

---

## 21. Decyzje architektoniczne

1. TLS nie jest omijany nawet w sieci prywatnej.
2. Prywatny CA musi spełniać współczesne wymagania X.509.
3. Android otrzymuje wyłącznie publiczny certyfikat CA.
4. Prywatne klucze, hasła i tokeny nie trafiają do repozytorium.
5. Operacje destrukcyjne mają wymagać jawnego działania użytkownika.
6. Upload domyślnie nie nadpisuje istniejących elementów.
7. Duże transfery będą później obsługiwane z progress, retry i resume.
8. Nie tworzymy nowego backend API tylko po to, aby powielić istniejący poprawny kontrakt.
9. Każda większa funkcja otrzymuje osobny build, test na urządzeniu i checkpoint Git.
10. Dokument ten jest kanonicznym worklogiem prac z 2026-10-04.

---

## 22. Dane wrażliwe

Dokument celowo nie zawiera:

- haseł
- prywatnych kluczy
- hasła do CA
- tokenów sesyjnych
- danych recovery
- konfiguracji SMTP
- prywatnego aliasu e-mail
- innych sekretów

Dokument będzie aktualizowany wraz z dalszym rozwojem RouterCloud Android.

---

## 23. Aktualizacja — bezpieczna zmiana nazwy

Zaimplementowano i przetestowano pełny flow zmiany nazwy plików i katalogów.

UI:

- menu `⋮` przy elementach, gdy backend zezwala na `allow_move`
- akcja `Zmień nazwę`
- dialog z walidacją nowej nazwy
- automatyczne odświeżenie katalogu po sukcesie

Walidacja klienta blokuje:

- pustą nazwę
- `.`
- `..`
- `/`
- `\`
- zmianę na identyczną nazwę
- nazwę już istniejącą w bieżącym katalogu

Backend wykorzystuje:

`MOVE`

z nagłówkiem:

`Destination`

Polityka backendu:

- rename tylko w obrębie tego samego katalogu
- brak przenoszenia pomiędzy katalogami
- brak nadpisania istniejącego celu
- konflikt istniejącej nazwy jest odrzucany

Test na fizycznym urządzeniu:

`PASS`

Potwierdzono:

- zmianę nazwy katalogu
- poprawne odświeżenie listy
- możliwość wejścia do przemianowanego katalogu
- ochronę przed zmianą na istniejącą nazwę

---

## 24. Aktualizacja — bezpieczne usuwanie

Zaimplementowano i przetestowano pełny flow usuwania plików i katalogów.

Network API:

- metoda HTTP `DELETE`
- obsługa poprawnego usunięcia
- obsługa błędów autoryzacji i uprawnień
- obsługa brakującego elementu
- brak lokalnego usunięcia elementu przed potwierdzeniem odpowiedzi backendu

UI:

- opcja `Usuń` w menu `⋮`
- opcja jest dostępna tylko wtedy, gdy backend zezwala na usuwanie
- respektowana flaga `allowDelete`
- obsługiwane:
  - `routercloud_allow_delete`
  - `allow_delete`

Bezpieczeństwo:

- operacja destrukcyjna wymaga jawnego potwierdzenia
- dialog wyświetla nazwę elementu
- użytkownik jest informowany, że operacji nie można cofnąć
- `Anuluj` nie wykonuje żadnej operacji sieciowej
- lista katalogu jest odświeżana dopiero po poprawnym zakończeniu `DELETE`

Test E2E na fizycznym urządzeniu:

`PASS`

Potwierdzono:

- usuwanie pliku
- usuwanie katalogu
- działanie przycisku `Anuluj`
- działanie końcowego potwierdzenia `Usuń`
- automatyczne odświeżenie listy po sukcesie
- zachowanie istniejącej funkcji zmiany nazwy

Stan:

- Delete network API: PASS
- Delete UI: PASS
- Delete E2E: PASS

---

## 25. Aktualizacja — bezpieczna sesja i odblokowanie odciskiem palca

Zaimplementowano i przetestowano trwałą sesję RouterCloud chronioną
mechanizmami Android Keystore i silnym uwierzytelnianiem biometrycznym.

### Model bezpieczeństwa

Aplikacja nie zapisuje hasła użytkownika.

Po poprawnym logowaniu zapisywany jest wyłącznie materiał aktywnej sesji
RouterCloud.

Sesja jest:

- eksportowana z pamięci klienta HTTP
- serializowana lokalnie
- szyfrowana AES/GCM
- chroniona kluczem przechowywanym w `AndroidKeyStore`
- zapisywana w `noBackupFilesDir`
- odszyfrowywana dopiero po silnym uwierzytelnieniu biometrycznym

Klucz Keystore wymaga:

`AUTH_BIOMETRIC_STRONG`

Nie są dopuszczone:

- `BIOMETRIC_WEAK`
- `DEVICE_CREDENTIAL`

W praktyce na urządzeniu testowym oznacza to odblokowanie odciskiem palca.

### Urządzenie testowe

Urządzenie zgłasza:

- fingerprint: `BIOMETRIC_STRONG`
- face: `BIOMETRIC_CONVENIENCE`

Dlatego Face Unlock nie spełnia polityki RouterCloud.

### Flow

Pierwsze użycie:

1. użytkownik loguje się loginem i hasłem
2. RouterCloud uzyskuje sesję
3. użytkownik potwierdza zapis sesji odciskiem palca
4. sesja zostaje zaszyfrowana i zapisana

Kolejne uruchomienie:

1. aplikacja wykrywa zaszyfrowaną sesję
2. wyświetlany jest `BiometricPrompt`
3. użytkownik potwierdza odciskiem palca
4. Keystore zezwala na odszyfrowanie
5. cookie są przywracane do klienta HTTP
6. aplikacja weryfikuje sesję przez `listDirectory()`
7. po sukcesie użytkownik trafia bezpośrednio do RouterCloud

Jeśli backend odrzuci zapisaną sesję:

- sesja lokalna jest kasowana
- użytkownik wraca do standardowego logowania

### Logout

Jawne `Wyloguj`:

- wykonuje logout backendu
- czyści sesję klienta
- usuwa zaszyfrowaną sesję lokalną
- po ponownym uruchomieniu aplikacji nie pojawia się automatyczne
  odblokowanie odciskiem

### E2E

Test na fizycznym urządzeniu:

`PASS`

Potwierdzono:

- logowanie hasłem
- zapis sesji po autoryzacji odciskiem
- force-stop aplikacji
- ponowne uruchomienie
- automatyczny prompt odcisku
- wejście do RouterCloud bez ponownego wpisywania hasła
- anulowanie promptu
- możliwość ponownej próby
- logout usuwa zapisaną sesję
- Face Unlock nie jest używany
- brak fallbacku do `DEVICE_CREDENTIAL`

Stan:

- Secure session: PASS
- Android Keystore: PASS
- AES/GCM storage: PASS
- BIOMETRIC_STRONG: PASS
- Fingerprint unlock: PASS
- Fingerprint E2E: PASS

---

## 26. Aktualizacja — respektowanie uprawnień backendu w UI

Przeprowadzono przegląd powiązania capability flags backendu RouterCloud
z aktualnie zaimplementowanymi operacjami Android UI.

Potwierdzono:

- `allowUpload`
  - steruje widocznością akcji `Wyślij plik`
  - steruje widocznością akcji tworzenia katalogu
  - jest wymagane dla `Wyślij tutaj` przy Android Share Target

- `allowMove`
  - steruje dostępnością `Zmień nazwę`

- `allowDelete`
  - steruje dostępnością `Usuń`

Backend dostarcza i klient parsuje również:

- `allowSearch`
- `allowArchive`
- `storagePresent`

Flagi te nie są obecnie wykorzystywane przez UI, ponieważ odpowiadające im
funkcje nie zostały jeszcze zaimplementowane. Nie są wystawiane żadne
nieautoryzowane akcje zastępcze.

Wniosek:

Aktualnie dostępne operacje destrukcyjne i modyfikujące są poprawnie
ograniczane przez capability flags backendu.

Stan:

- Upload permissions: PASS
- Create directory permissions: PASS
- Rename permissions: PASS
- Delete permissions: PASS
- Android Share upload permissions: PASS
- Backend permissions respected in UI: PASS

---

## 27. Aktualizacja — informacje o zajętości przestrzeni

Zaimplementowano i przetestowano prezentację informacji o przestrzeni
dyskowej RouterCloud.

Backend zwraca obiekt:

`storage`

z polami:

- `total`
- `used`
- `available`

Klient Android mapuje dane do modelu:

`RouterCloudStorage`

UI:

- dodano pierwszy właściwy kafel w stylistyce RouterCloud Metro
- kafel ma ostre narożniki zgodne z wersją webową
- prezentuje:
  - zajęte miejsce
  - procent wykorzystania
  - wolne miejsce
  - pojemność całkowitą
- procent wykorzystania prezentowany jest z dokładnością do jednego
  miejsca po przecinku

Przykładowy zweryfikowany stan backendu:

- total: 423466610688 B
- used: 2227953664 B
- available: 399652462592 B

Test na fizycznym urządzeniu:

`PASS`

Stan:

- Storage backend parsing: PASS
- Storage model: PASS
- Storage UI: PASS
- Metro sharp-corner styling: PASS
- Storage E2E: PASS

---

## 28. Aktualizacja — ikony plików w stylu Metro / Windows Mobile

Domyślne ikony Material Design zostały zastąpione własnym zestawem
wektorowych ikon RouterCloud inspirowanych językiem wizualnym
Windows 10 Mobile / Metro.

Cel:

- odejście od wyglądu typowego dla współczesnego Androida
- spójność z RouterCloud Web
- płaski, geometryczny styl
- ostre krawędzie
- brak cieni i dekoracyjnych efektów
- wspólna rodzina wizualna dla wszystkich typów plików

Zaimplementowane typy:

- katalog
- PDF
- obraz
- wideo
- audio
- archiwum
- kod / skrypt
- dokument tekstowy
- plik ogólny

Foldery używają płaskiego żółtego glyphu.

Pozostałe typy plików używają prostych białych ikon konturowych.

Usunięto zależność:

`material-icons-extended`

Ikony są renderowane przez własny komponent:

`MetroFileIcon`

i własne operacje `Canvas`.

Test wizualny na fizycznym urządzeniu:

`PASS`

Potwierdzono:

- spójny wygląd katalogów
- poprawną ikonę PDF
- poprawne ikony plików tekstowych
- poprawną ikonę obrazu
- zgodność stylistyczną z kierunkiem Metro RouterCloud

Dalsze dopracowanie ikon pozostaje jako późniejszy polish:

- możliwe uproszczenie glyphu PDF
- dalsze strojenie grubości linii
- ewentualne korekty rozmiaru i optycznego wyrównania
- dopracowanie pozostałych rzadziej używanych typów

Stan:

- Custom Metro icons: PASS
- File type detection: PASS
- Material icon replacement: PASS
- Physical-device visual test: PASS

---

## 29. Aktualizacja — Metro UI v2 i kafle akcji

Przebudowano górną część ekranu plików w kierunku
Windows 10 Mobile / Metro.

Zmiany:

- większy nagłówek RouterCloud
- uproszczona hierarchia ścieżki i licznika elementów
- dwa prostokątne kafle akcji:
  - Wyślij plik
  - Katalog
- zachowane ostre narożniki
- brak cieni i zaokrągleń
- układ dwóch kafli obok siebie
- kafel Pamięć pozostaje szerokim kaflem informacyjnym

Tekstowe symbole akcji zostały zastąpione własnymi glyphami
renderowanymi przez Canvas:

- Upload
- New Folder

Glyphy korzystają z tego samego płaskiego, geometrycznego języka
co własne ikony typów plików RouterCloud.

Test wizualny na fizycznym urządzeniu:

`PASS`

Stan:

- Metro header: PASS
- Metro action tiles: PASS
- Custom action glyphs: PASS
- Physical-device visual test: PASS

Kolejny etap:

- zmiana rozmiaru kafli
- Small / Medium / Wide / Large
- zapis układu pomiędzy uruchomieniami
- interakcja przez długie przytrzymanie

---

## 30. Aktualizacja — rozmiary kafli i gęstość Windows 10 Mobile

Dodano mechanizm rozmiarów kafli inspirowany Windows 10 Mobile.

Obsługiwane rozmiary:

- Mały
- Średni
- Szeroki

Usunięto wcześniejszy wariant Duży, który odpowiadał bardziej
desktopowemu Windows 10 niż Windows 10 Mobile.

Dodano dwa tryby gęstości:

- domyślny: 6 małych jednostek szerokości
- Pokaż więcej kafelków: 8 małych jednostek szerokości

Domyślny wariant pozwala zachować większe kafle typowe dla
Windows 10 Mobile.

Tryb Pokaż więcej kafelków umożliwia gęstszy układ,
w tym dwa szerokie kafle obok siebie.

Rozmiary kafli oraz wybór gęstości są zapisywane lokalnie
i zachowywane po ponownym uruchomieniu aplikacji.

Test wizualny na fizycznym urządzeniu:

PASS

Stan:

- Small / Medium / Wide: PASS
- 6-unit layout: PASS
- 8-unit Show more tiles layout: PASS
- persistent tile size: PASS
- persistent density setting: PASS

Następny etap:

- tryb edycji kafli
- drag and drop / zmiana kolejności
- trwały zapis kolejności

---

## 31. Aktualizacja — interaktywny dashboard Metro

Rozwinięto dashboard RouterCloud w kierunku ekranu Start
Windows 10 Mobile.

Zaimplementowano:

- rozmiary kafli:
  - Mały
  - Średni
  - Szeroki
  - eksperymentalny Duży
- dwa tryby gęstości:
  - standardowy układ 6 jednostek
  - Pokaż więcej kafelków — 8 jednostek
- trwały zapis rozmiarów kafli
- trwały zapis ustawienia gęstości
- tryb Edytuj kafelki
- drag and drop
- zmiana kolejności kafli na żywo podczas przeciągania
- trwały zapis kolejności
- osobna warstwa drag overlay dla przeciąganego kafla
- stabilny placeholder w FlowRow
- animacja reflow pozostałych kafli
- własne glyphy Metro dla akcji
- eksperymentalny kafel Duży 4x4

Warstwa drag overlay rozwiązuje problem migania przeciąganego
kafla podczas reorganizacji elementów o różnych rozmiarach.

Pozostałe kafle animują zmianę pozycji podczas live reorder.

Test na fizycznym urządzeniu:

PASS

Potwierdzono:

- Mały -> Szeroki drag: PASS
- Średni -> Szeroki drag: PASS
- live reorder: PASS
- persistent tile order: PASS
- reflow animation: PASS
- Duży kafel: PASS wizualny
- 6-unit layout: PASS
- 8-unit Show more tiles: PASS

Decyzja projektowa:

- Small / Medium / Wide mają docelowo możliwie wiernie
  nawiązywać do Windows 10 Mobile.
- Large pozostaje rozszerzeniem eksperymentalnym RouterCloud.
- kolejny etap to Windows-style visual polish typografii,
  ikon, paddingów i układu treści kafli.

## Metro Tile Engine - checkpoint TDD

### Cel

Przebudowa mechaniki kafelkow RouterCloud na jawny,
ograniczony workspace zgodny z:

- docs/METRO-TILE-ENGINE-SPEC.md
- grid 6 / 8 jednostek
- workspaceRows = 4
- brak kolizji
- brak zwiekszania wysokosci dashboardu przez drag
- atomowe ACCEPT / REJECT
- wspolny resolver docelowo dla drag i resize

### Aktualny model

Dodano validator:

- validateMetroTileLayout()

Sprawdza:

- komplet pozycji i rozmiarow
- granice poziome
- granice pionowe
- workspaceRows
- nakladanie kafelkow

Validator posiada testy JVM.

Plik:

- app/src/test/java/com/wojko6/routercloud/MetroTileLayoutValidatorTest.kt

Aktualny wynik:

- validator tests = PASS
- all unit tests przed T08 = PASS
- assembleDebug przed T08 = PASS

### Nowy resolver

Plik:

- app/src/main/java/com/wojko6/routercloud/MetroTileResolver.kt

Funkcja:

- resolveMetroTileLayoutChange()

Aktualnie zaimplementowane zachowanie:

T01 PASS

Medium przeciagany na puste miejsce:

- moving tile trafia dokladnie w target
- pozostale kafelki nie zmieniaja pozycji
- caly kandydat przechodzi validateMetroTileLayout()

T02 PASS

Medium przeciagany dokladnie na drugi Medium:

- moving tile przejmuje target
- displaced tile przechodzi w poprzednie miejsce moving tile
- naturalny swap
- validateMetroTileLayout() = PASS

T12 PASS

Medium przeciagany ponizej workspaceRows = 4:

- resolver zwraca REJECT
- poprzedni layout pozostaje bez zmian
- dashboard nie moze legalnie rosnac w dol

### Aktualny RED

T08:

- t08_mediumPartiallyOverlapsMedium_displacesIntoVacatedSlot

Scenariusz:

- tile A = Medium, column 0, row 0
- tile B = Medium, column 4, row 0
- B jest przeciagany do column 1, row 0
- B tylko czesciowo nachodzi na A

Oczekiwane:

- B zajmuje column 1, row 0
- A zostaje displaced
- A trafia do poprzedniego miejsca B: column 4, row 0
- finalny layout przechodzi validator

Aktualny wynik:

- T08_RC=1
- T08_RED_PHASE=PASS

To jest oczekiwany RED.

Przyczyna:

Aktualna implementacja resolvera rozpoznaje displaced tile tylko wtedy,
gdy jego pozycja jest dokladnie rowna requestedPosition.

Nie wykrywa jeszcze rzeczywistego przeciecia prostokatow kafelkow.

### Nastepny krok

NIE podpinac jeszcze nowego resolvera do UI.

Nastepna implementacja ma:

1. wykrywac geometryczne overlap kafelkow
2. zachowac T01 PASS
3. zachowac T02 PASS
4. zachowac T12 PASS
5. doprowadzic T08 do GREEN
6. uruchomic wszystkie unit testy
7. uruchomic assembleDebug

Po T08 GREEN dopiero tworzymy checkpoint kodu.

### Stara logika nadal obecna

MainActivity.kt nadal zawiera stary mechanizm:

- canPlaceMetroTile()
- findNearestFreeMetroTilePosition()
- metroTilesOverlap()
- isMetroTileInsideGrid()
- resolveMetroTileMove()

Problemy starej logiki:

- findNearestFreeMetroTilePosition() moze szukac kolejnych wierszy bez ograniczenia workspace
- isMetroTileInsideGrid() sprawdza szerokosc, ale nie workspaceRows
- drag i resize nadal korzystaja z roznych sciezek
- applyMetroTileSizeChange() nadal uzywa findNearestFreeMetroTilePosition()

Nie usuwac tych funkcji przed zakonczeniem i przetestowaniem nowego resolvera.

### Stan plikow na checkpoint

Oczekiwane zmiany robocze:

- M app/src/main/java/com/wojko6/routercloud/MainActivity.kt
- ?? app/src/main/java/com/wojko6/routercloud/MetroTileResolver.kt
- ?? app/src/test/java/com/wojko6/routercloud/MetroTileLayoutResolverTest.kt
- ?? app/src/test/java/com/wojko6/routercloud/MetroTileLayoutValidatorTest.kt

### Zasada dalszej pracy

Nie dodajemy nowych funkcji dashboardu.

Najpierw:

- Tile Engine
- T01-T24
- validateLayout PASS
- stabilny drag
- stabilny resize
- persistence
- dopiero potem nowe kafelki lub funkcje.



## Metro Tile Engine - GREEN checkpoint T08

Stan po implementacji geometrycznych kolizji jednego displaced tile.

### Testy resolvera

T01 PASS

- Medium -> wolny target
- moving tile trafia dokladnie w requestedPosition
- pozostale kafelki nie zmieniaja pozycji

T02 PASS

- Medium -> drugi Medium
- naturalny swap
- displaced tile trafia w zwolnione miejsce moving tile

T08 PASS

- czesciowe nachodzenie Medium na Medium
- kolizja jest wykrywana geometrycznie
- nie jest wymagane identyczne column,row
- displaced tile trafia w poprzednie miejsce moving tile
- finalny layout przechodzi validator

T12 PASS

- ruch ponizej workspaceRows = 4
- resolver zwraca REJECT
- nie powstaja dodatkowe wiersze

### Regression status

- T01 = PASS
- T02 = PASS
- T08 = PASS
- T12 = PASS
- wszystkie unit testy = PASS
- assembleDebug = PASS

### Implementacja

Nowy resolver:

- app/src/main/java/com/wojko6/routercloud/MetroTileResolver.kt
- resolveMetroTileLayoutChange()

Nowy resolver uzywa:

- rzeczywistego rectangle overlap
- validateMetroTileLayout()
- jawnego gridUnits
- jawnego workspaceRows

Na tym etapie resolver obsluguje maksymalnie jeden displaced tile.

Wiele jednoczesnych kolizji nadal nie jest zaimplementowane.

### Nastepny krok

Nastepny etap TDD:

- Wide lub Large nachodzi jednoczesnie na dwa kafelki
- caly reflow musi byc atomowy
- albo wszystkie displaced tiles znajda legalne pozycje
- albo caly ruch zostanie REJECT

Nie podpinamy jeszcze nowego resolvera do UI.

Najpierw rozwijamy i testujemy czysty silnik.



## Metro Tile Engine - GREEN checkpoint T07

Dodano atomowy multi-reflow dla wielu displaced tiles.

### T07 PASS

Scenariusz:

- Large znajduje sie po prawej stronie grid 8
- dwa Medium znajduja sie pionowo po lewej
- Large jest przeciagany na obszar obu Medium

Wynik:

- Large zajmuje requestedPosition
- oba Medium zostaja wykryte jako displaced
- reflow odbywa sie w jednej transakcji
- pierwszy displaced preferuje pozycje zwolniona przez moving tile
- kolejne displaced szukaja najblizszego legalnego miejsca
- wyszukiwanie jest ograniczone do workspaceRows
- finalny layout przechodzi validateMetroTileLayout()

### Atomowosc

Resolver buduje working copy layoutu.

Zmiany nie sa zwracane, dopoki:

- wszystkie displaced tiles nie otrzymaja legalnych pozycji
- caly kandydat nie przejdzie validatora

Jesli dowolny displaced tile nie moze zostac umieszczony:

- resolver zwraca null
- operacja ma zostac potraktowana jako REJECT
- nie istnieje czesciowo zaakceptowany layout

### Bounded search

Nowe wyszukiwanie wolnej pozycji:

- respektuje gridUnits
- respektuje workspaceRows
- nie tworzy nowych wierszy
- jest deterministyczne
- przy remisie preferuje mniejszy row, potem column

### Regression status

- T01 = PASS
- T02 = PASS
- T07 = PASS
- T08 = PASS
- T12 = PASS
- resolver tests = PASS
- all unit tests = PASS
- assembleDebug = PASS

### Nastepny krok

T14:

Brak miejsca na pelny reflow.

Oczekiwane:

- resolver zwraca REJECT
- zadna czesc operacji nie zostaje zaakceptowana
- poprzedni layout pozostaje poprawny

Nowy resolver nadal nie jest podpiety do UI.

Najpierw konczymy czysty Tile Engine i jego testy.



## Metro Tile Engine - GREEN checkpoint T14

### T14 PASS

Przetestowano sytuacje, w ktorej moving tile wypiera kafelek,
ale w ograniczonym workspace nie istnieje miejsce na pelny reflow.

Wynik:

- resolver zwraca null
- operacja jest REJECT
- poprzedni layout pozostaje niezmieniony
- nie istnieje czesciowo zaakceptowany reflow
- workspaceRows = 4 pozostaje twarda granica
- validateMetroTileLayout() poprzedniego layoutu nadal = PASS

### Aktualny status Tile Engine

- T01 = PASS
- T02 = PASS
- T07 = PASS
- T08 = PASS
- T12 = PASS
- T14 = PASS
- resolver tests = PASS
- all unit tests = PASS
- assembleDebug = PASS

### Potwierdzone wlasciwosci

- ruch na wolne miejsce
- naturalny swap
- geometryczne wykrywanie kolizji
- multi-collision reflow
- bounded nearest-free search
- brak tworzenia dodatkowych wierszy
- atomowe ACCEPT / REJECT
- finalny validator przed ACCEPT

### Nastepny etap

Resize.

Resize musi korzystac z tego samego:

- resolveMetroTileLayoutChange()
- validateMetroTileLayout()
- bounded workspace
- atomowego reflow

Stary applyMetroTileSizeChange() nadal nie jest migrowany.

Najpierw tworzymy testy resize T15-T18.
Dopiero potem podpinamy resolver do UI.



## Metro Tile Engine - GREEN checkpoint resize T15-T18

Przetestowano resize przez ten sam resolver,
ktory obsluguje drag i reflow.

### T15 PASS

Resize bez kolizji:

- Small -> Medium
- pozycja top-left zostaje zachowana
- pozostale kafelki nie ruszaja sie
- requestedSize zostaje zaakceptowany
- finalny layout przechodzi validator

### T16 PASS

Resize z jedna kolizja:

- powiekszany kafelek ma pierwszenstwo
- sasiad zostaje displaced
- sasiad otrzymuje legalna pozycje
- caly wynik przechodzi validator

### T17 PASS

Resize z wieloma kolizjami:

- Medium -> Large
- dwa Medium zostaja jednoczesnie displaced
- reflow jest atomowy
- wszystkie kafelki musza znalezc legalne pozycje
- finalny layout przechodzi validator

### T18 PASS

Resize wychodzacy poza workspace:

- resolver zwraca null
- resize jest REJECT
- stary rozmiar pozostaje bez zmian
- stara pozycja pozostaje bez zmian
- workspaceRows = 4 jest respektowane

### Aktualny status

- T01 = PASS
- T02 = PASS
- T07 = PASS
- T08 = PASS
- T12 = PASS
- T14 = PASS
- T15 = PASS
- T16 = PASS
- T17 = PASS
- T18 = PASS
- resolver tests = PASS
- validator tests = PASS
- all unit tests = PASS
- assembleDebug = PASS

### Nastepny krok

Integracja nowego Tile Engine z UI.

Kolejnosc:

1. podpiac resize pod resolveMetroTileLayoutChange()
2. usunac z resize uzycie findNearestFreeMetroTilePosition()
3. zachowac atomowe ACCEPT / REJECT
4. zapis persistence tylko po ACCEPT
5. test manualny na telefonie
6. dopiero potem przepiac drag

Stary resolver drag pozostaje tymczasowo bez zmian,
dopoki resize nie zostanie zweryfikowany w UI.



## Metro Tile Engine - GREEN UI integration checkpoint

Nowy bounded Tile Engine zostal podpiety do rzeczywistego UI.

### Resize

- resize korzysta z resolveMetroTileLayoutChange()
- workspaceRows = 4
- ACCEPT zmienia stan i persistence
- REJECT pozostawia poprzedni legalny layout
- gdy nastepny rozmiar w cyklu jest niemozliwy,
  UI szuka kolejnego legalnego rozmiaru
- startup migration naprawia stare nielegalne layouty

### Drag

- drag korzysta z resolveMetroTileLayoutChange()
- live preview jest liczony z dragStartPositions
- brak kumulacyjnego reflow drift
- target poza workspace jest REJECT
- overlay moze podazac za palcem poza workspace,
  ale logiczny layout pozostaje legalny
- onDragEnd zapisuje tylko layout przechodzacy validator
- onDragCancel przywraca dokladny dragStartPositions

### Workspace

MetroPositionedLayout ma stala wysokosc:

- METRO_WORKSPACE_ROWS = 4
- pozycje kafelkow nie steruja juz wysokoscia dashboardu
- przeciaganie kafelka w dol nie moze przesuwac listy plikow
- lista plikow pozostaje stabilna podczas drag

### Walidacja manualna na urzadzeniu

PASS:

- resize kafelkow
- resize z reflow
- drag kafelkow
- drag na zajete miejsce
- drag poza dolna granice
- drag poza pozostale granice
- lista plikow nie jest spychana w dol
- dashboard nie rosnie podczas drag
- layout zachowuje sie prawidlowo po operacjach

### Stan architektury

Aktywna sciezka UI:

- resize -> resolveMetroTileLayoutChange()
- drag -> resolveMetroTileLayoutChange()
- startup -> validateMetroTileLayout()
- startup fallback -> bounded packer
- persistence drag -> tylko valid layout

Pozostaly jeszcze stare funkcje legacy w MainActivity.kt,
ale nie powinny byc juz potrzebne przez aktywna sciezke UI.

Nastepny krok:

1. potwierdzic brak aktywnych wywolan legacy
2. usunac resolveMetroTileMove()
3. usunac findNearestFreeMetroTilePosition()
4. usunac isMetroTileInsideGrid()
5. usunac nieograniczony buildPackedMetroTilePositions()
6. ponownie uruchomic testy i build
7. wykonac krotki smoke test na telefonie



## Metro Tile Engine - GREEN bounded drag and adaptive resize

Finalna sciezka UI zostala zweryfikowana manualnie
na urzadzeniu.

### Drag

- drag korzysta z resolveMetroTileLayoutChange()
- logiczny workspace jest ograniczony do 4 wierszy
- overlay moze podazac za palcem poza workspace
- pozycja logiczna poza workspace jest odrzucana
- dashboard nie zwieksza wysokosci podczas drag
- lista plikow pozostaje na stalej pozycji
- reflow miedzy kafelkami dziala prawidlowo
- onDragCancel przywraca stan poczatkowy
- onDragEnd zapisuje tylko legalny layout

### Resize

- resize korzysta z tego samego Tile Engine co drag
- kolizje sa rozwiazywane atomowo
- niemozliwy rozmiar nie psuje layoutu
- sterowanie nie blokuje sie na niemozliwym rozmiarze
- kafelek przy prawej lub dolnej krawedzi moze zmienic
  anchor na najblizsza legalna pozycje podczas resize
- np. Medium -> Wide nie jest blokowane tylko dlatego,
  ze aktualny lewy-gorny anchor lezy zbyt blisko krawedzi

### Startup

- stare nielegalne layouty sa walidowane
- fallback korzysta z bounded packera
- zapis nie moze wychodzic poza workspace

### Manual smoke test

PASS:

- resize
- resize przy krawedzi
- drag na wolne miejsce
- drag na zajete miejsce
- reflow
- drag poza workspace
- stala wysokosc dashboardu
- brak spychania listy plikow

Pozostale zadania Tile Engine:

- persistence rozmiarow osobno dla grid 6 i grid 8
- testy persistence i density switch T20-T23
- finalny invariant/stress test T24

