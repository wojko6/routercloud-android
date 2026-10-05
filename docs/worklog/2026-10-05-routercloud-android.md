# RouterCloud Android — worklog 2026-10-05

## Zakres dnia

Dzisiejsza praca skupiła się na uproszczeniu mobilnego interfejsu
RouterCloud oraz dodaniu dwóch funkcjonalnych elementów:
Ulubionych i pierwszej wersji synchronizacji telefonu z RouterCloud.

## Compact Mobile Home

Ekran Start został przebudowany z rozbudowanego układu kafelkowego
na kompaktowy interfejs mobilny.

Wprowadzono:

- kompaktowy układ 2 × 2:
  - Pliki,
  - Prześlij,
  - Nowy folder,
  - Ostatnie,
- skróconą kartę wykorzystania pamięci,
- listę ostatnich elementów,
- dolny pasek nawigacyjny,
- spójną ikonografię Metro,
- MetroFileIcon również w sekcji ostatnich plików,
- własne glyphy dla nawigacji zamiast znaków tekstowych.

Starszy rozbudowany Metro Tile Dashboard pozostaje w kodzie jako
istniejąca implementacja i nie został usunięty w ramach tego refaktoru.

Checkpoint:
- 9926e77 — feat(android): add compact mobile home and metro navigation

## Ulubione

Dodano trwały system Ulubionych.

Funkcjonalność obejmuje:

- dodawanie pliku lub folderu do Ulubionych z menu elementu,
- usuwanie z Ulubionych,
- trwały zapis lokalny,
- osobną kartę Ulubione w dolnej nawigacji,
- wyświetlanie właściwego MetroFileIcon,
- prezentowanie ścieżki źródłowej,
- otwieranie plików z pełnej zapisanej ścieżki,
- przechodzenie bezpośrednio do ulubionych folderów.

Zweryfikowano również zachowanie stanu po ponownym uruchomieniu aplikacji.

Checkpoint:
- a07a1f0 — feat(android): add persistent favorites

## Synchronizacja v1

Powstała pierwsza rzeczywista implementacja synchronizacji:

Telefon → RouterCloud

### Architektura

Dodano:

- RouterCloudSyncStore
  - przechowuje konfigurację synchronizacji,
  - zapamiętuje URI folderu SAF,
  - zapisuje docelową ścieżkę RouterCloud,
  - przechowuje czas ostatniej poprawnej synchronizacji;

- RouterCloudLocalTreeScanner
  - rekurencyjnie skanuje folder wybrany przez Android Storage Access Framework;

- RouterCloudSyncManifestStore
  - zapisuje lokalny manifest plików;
  - fingerprint v1 wykorzystuje:
    - rozmiar,
    - czas modyfikacji;

- RouterCloudSyncEngine
  - tworzy brakujące katalogi,
  - wysyła nowe pliki,
  - ponownie wysyła zmienione pliki,
  - pomija pliki uznane za niezmienione,
  - sprawdza obecność i rozmiar pliku po stronie RouterCloud.

### SAF

Folder lokalny wybierany jest przez ACTION_OPEN_DOCUMENT_TREE.

Aplikacja zachowuje trwałe uprawnienie do wybranego drzewa dokumentów.
Nie było potrzeby dodawania szerokiego uprawnienia do pamięci telefonu.

### Cel zdalny

Domyślny katalog:
- /MobileSync

### Zasady bezpieczeństwa v1

Synchronizacja jest świadomie jednokierunkowa.

Nie wykonuje automatycznego:
- kasowania lokalnych plików,
- kasowania plików w RouterCloud,
- synchronizacji RouterCloud → telefon,
- rozwiązywania konfliktów.

## Walidacja synchronizacji

Kontrolowany test wykonano na folderze:
- /sdcard/Nowy/Nowy1

Przeprowadzono trzy testy:

1. Nowy plik
   - wysłano: 1
   - pominięto: 0
   - PASS

2. Brak zmian
   - wysłano: 0
   - pominięto: 1
   - PASS

3. Zmodyfikowany plik
   - wysłano: 1
   - pominięto: 0
   - PASS

Potwierdzono więc pełny podstawowy cykl:
- NEW → SKIP UNCHANGED → UPLOAD CHANGED

## Znane ograniczenia Sync v1

Obecna wersja:

- działa ręcznie przez „Synchronizuj teraz”,
- nie korzysta jeszcze z WorkManagera,
- nie działa okresowo w tle,
- nie ma checksum,
- nie wykorzystuje ETag,
- nie ma pełnego modelu konfliktów,
- nie jest dwukierunkowa,
- nie propaguje usunięć.

## Następny etap

Plan dalszych prac:

1. osobny, dopracowany widok Synchronizacji;
2. widoczna data ostatniej poprawnej synchronizacji;
3. status i podsumowanie ostatniego przebiegu;
4. konfiguracja katalogu docelowego;
5. testy silnika i manifestu;
6. WorkManager / synchronizacja w tle;
7. dopiero później analiza synchronizacji dwukierunkowej i konfliktów.

## Stan na koniec dnia

Najważniejsze nowe elementy aplikacji:

- kompaktowy mobilny Home,
- spójna ikonografia Metro,
- dolna nawigacja,
- trwałe Ulubione,
- ręczna synchronizacja Telefon → RouterCloud v1.

Build Androida: PASS.
