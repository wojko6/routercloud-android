# RouterCloud Metro Tile Folders - Behavior Specification v0.1

Status: implementation target

## 1. Zakres

Specyfikacja obejmuje pierwszą wersję folderów kafelków
na dashboardzie RouterCloud Android.

Folder v1 obejmuje:

- tworzenie folderu z dwóch kafelków
- dodawanie kafelka do istniejącego folderu
- otwieranie folderu
- uruchamianie akcji kafelków znajdujących się w folderze
- persistence składu folderu
- persistence kafelka-folderu
- współpracę z grid 6 i grid 8
- bezpieczne rozwiązanie folderu
- odporność na uszkodzony zapis

Poza zakresem Folder v1:

- foldery wewnątrz folderów
- merge folder -> folder
- Live Tiles
- nowe typy kafelków
- zmiana zachowania istniejącego Tile Engine T01-T24
- backendowe katalogi RouterCloud
- synchronizacja folderów z systemem plików

Foldery kafelków są wyłącznie lokalną warstwą organizacji
dashboardu Android.

---

## 2. Fundament

Metro Tile Engine v1 jest zamknięty.

Stan bazowy:

    T01-T24 = PASS

Foldery nie mogą zmieniać semantyki istniejącego resolvera.

Resolver nadal operuje wyłącznie na:

    tileId
    position
    size

Nie zna pojęcia folderu.

Dla resolvera kafelek-folder jest zwykłym top-level tileId.

---

## 3. Model logiczny

Istnieją dwa rodzaje elementów dashboardu:

    leaf tile
    folder tile

Leaf tile to normalny kafelek funkcji RouterCloud, np.:

    upload
    directory
    storage

Folder ma własny stabilny identyfikator:

    folder_<uuid>

Przykład:

    folder_550e8400-e29b-41d4-a716-446655440000

Folder posiada:

    id
    name
    childTileIds

Minimalna liczba elementów folderu:

    2

Ten sam leaf tile:

- może być top-level
- albo należeć do dokładnie jednego folderu

Nigdy do obu jednocześnie.

---

## 4. Twarde invarianty folderów

Każdy zaakceptowany stan MUSI spełniać:

### Invariant F-A — brak duplikacji

Jeden leaf tile występuje dokładnie raz:

    top-level

albo:

    wewnątrz jednego folderu

### Invariant F-B — brak nesting

Folder nie może być dzieckiem innego folderu.

### Invariant F-C — minimum dwóch dzieci

Trwały folder nie może zawierać mniej niż dwóch leaf tiles.

Jeżeli operacja pozostawiłaby jedno dziecko,
folder musi zostać rozwiązany atomowo.

### Invariant F-D — Tile Engine pozostaje poprawny

Każdy top-level layout po operacji folderowej musi przejść:

    validateMetroTileLayout(...)

### Invariant F-E — atomowość

Każda operacja folderowa daje:

    ACCEPT

albo:

    REJECT

Nie istnieje stan pośredni, w którym:

- tile jest jednocześnie w folderze i top-level
- folder istnieje bez poprawnego layoutu
- layout zawiera kolizje
- część persistence została zapisana, a część nie

---

## 5. Top-level tile registry

Obecny statyczny model:

    upload
    directory
    storage

zostaje rozszerzony o dynamiczne folder IDs.

Top-level IDs są wyliczane jako:

    dostępne leaf tiles
    MINUS
    leaf tiles należące do folderów
    PLUS
    folder tile IDs

Resolver otrzymuje wyłącznie aktualne top-level IDs.

---

## 6. Tworzenie folderu

Zwykłe przeciągnięcie kafelka na inny kafelek
NADAL oznacza normalny Tile Engine reflow.

Folder nie może powstawać przez przypadkowy overlap.

Folder creation wymaga świadomego hover gesture.

Warunki:

1. source jest leaf tile
2. target jest innym leaf tile albo istniejącym folderem
3. środek przeciąganego kafelka znajduje się w centralnej
   strefie targetu
4. source pozostaje nad tym samym targetem przez:

    METRO_FOLDER_DWELL_MS = 650

5. po upływie czasu target przechodzi do stanu:

    FOLDER_TARGET_ARMED

6. dopiero zakończenie drag na uzbrojonym target
   wykonuje operację folderową

Jeżeli warunek dwell nie został spełniony:

    zachowujemy zwykły resolver/reflow

---

## 7. Centralna strefa targetu

Folder hover nie powinien aktywować się od lekkiego
zahaczenia o krawędź kafelka.

Do folder gesture używana jest centralna strefa targetu.

Target zone:

    centralne 60% szerokości
    centralne 60% wysokości

Overlap poza tą strefą:

    normalny Tile Engine

Dwell resetuje się gdy:

- pointer opuści target zone
- zmieni się target
- drag zostanie anulowany

---

## 8. Leaf -> leaf

Po uzbrojeniu targetu i zakończeniu drag:

    source leaf + target leaf
        ->
    nowy folder

Folder:

- otrzymuje nowe stabilne folderId
- zawiera source i target
- zajmuje pozycję targetu
- początkowo przyjmuje rozmiar targetu

Source i target znikają z top-level registry.

Ich własne zapisane rozmiary nie są kasowane.

Pozwala to zachować ich stan na wypadek późniejszego
rozwiązania folderu.

---

## 9. Leaf -> istniejący folder

Po uzbrojeniu istniejącego folderu:

    source leaf
        ->
    child istniejącego folderu

Folder:

- zachowuje swoje folderId
- zachowuje pozycję
- zachowuje rozmiar

Source znika z top-level layoutu.

Pozostałe top-level kafelki nie są przesuwane bez potrzeby.

---

## 10. Folder jako source

W Folder v1:

    folder -> leaf

nie tworzy folderu.

    folder -> folder

nie wykonuje merge.

Obie operacje pozostają zwykłymi operacjami
Tile Engine drag/reflow.

Nested folders są zabronione.

---

## 11. Otwieranie folderu

Tap na folder w normalnym trybie:

    otwiera folder

Folder prezentuje wszystkie child tiles.

Każde dziecko:

- zachowuje swoją oryginalną akcję
- zachowuje swój typ
- nie staje się kopią top-level tile

Przykład:

    Upload w folderze
        ->
    ta sama akcja upload

    Directory w folderze
        ->
    ta sama akcja create directory

Folder UI nie zmienia backend authorization.

---

## 12. Edit mode

Long press na folder:

    aktywuje edit mode

Folder jako top-level tile:

- można przesuwać
- można resize
- korzysta z istniejącego Tile Engine

Rozmiary pozostają:

    Small
    Medium
    Wide
    Large

Resize folderu nie zmienia rozmiarów dzieci.

---

## 13. Rozwiązanie folderu

Folder musi mieć bezpieczną drogę powrotu.

Folder v1 udostępnia jawne działanie:

    Rozwiąż folder

Operacja jest atomowa.

Po rozwiązaniu:

- folder znika
- wszystkie child tiles wracają do top-level registry
- ich zapisane rozmiary zostają zachowane
- tworzony jest legalny top-level layout

Preferencja placement:

1. wykorzystaj aktualną pozycję folderu dla pierwszego dziecka,
   jeżeli pasuje
2. pozostałe dzieci umieszczaj deterministycznie
   w najbliższych legalnych wolnych pozycjach
3. cały wynik musi przejść validateMetroTileLayout

Jeżeli wszystkich dzieci nie można legalnie umieścić:

    REJECT

Folder pozostaje bez zmian.

---

## 14. Persistence folder membership

Skład folderów NIE zależy od grid width.

Przełączenie:

    grid 6 -> grid 8
    grid 8 -> grid 6

nie może zmieniać membership.

Przykładowy klucz:

    tile_folders_v1

Format powinien być strukturalny,
preferencyjnie JSON.

Nie używamy formatu zależnego od przypadkowego
kolejnego separatora String.split.

---

## 15. Persistence pozycji

Pozycje top-level nadal są osobne dla grid:

    tile_positions_*_6
    tile_positions_*_8

Loader musi akceptować aktualny zbiór legalnych
top-level IDs:

- aktualne leaf tiles
- aktualne folder IDs

Nie może ograniczać się wyłącznie do:

    defaultMetroTileOrder()

Uszkodzony lub nieznany ID nie może spowodować
renderowania nielegalnego layoutu.

---

## 16. Persistence rozmiarów

Folder korzysta z istniejącego mechanizmu:

    tile_size_v2_<gridUnits>_<tileId>

Przykład:

    tile_size_v2_6_folder_<uuid>
    tile_size_v2_8_folder_<uuid>

Rozmiar folderu jest niezależny pomiędzy grid 6 i 8.

Membership folderu jest wspólny.

---

## 17. Persistence kolejności

Obecny tile_order oparty wyłącznie na statycznych
default IDs nie może być źródłem prawdy dla folderów.

Folder v1 wymaga kolejności obsługującej:

    leaf IDs
    folder IDs

Przy restore:

- znane top-level IDs są zachowywane
- nieznane IDs są ignorowane
- brakujące legalne IDs są dopisywane deterministycznie
- child tiles nie mogą pojawić się top-level

---

## 18. Restore po restarcie

Po restarcie aplikacji muszą zostać zachowane:

- membership folderów
- folder IDs
- pozycje folderów
- rozmiary folderów
- top-level order

Restore przebiega w kolejności logicznej:

1. load folder state
2. validate folder state
3. wylicz legal top-level tile IDs
4. load top-level order
5. load per-grid positions
6. load per-grid sizes
7. validate cały layout
8. render

Nie renderujemy stanu przed walidacją.

---

## 19. Invalid persisted state

Przykładowe błędy:

- jeden tile w dwóch folderach
- folder zawiera folderId
- folder ma mniej niż dwóch członków
- nieznany child tile
- duplicate child
- nieznany top-level folder ID
- brak pozycji folderu
- kolizja po restore
- folder poza workspace

Wynik:

    SAFE FALLBACK

Nigdy:

    częściowo poprawiony stan z ukrytą kolizją

Fallback musi być deterministyczny.

---

## 20. Relacja do starego Tile Engine

Nie zmieniamy semantyki:

    T01-T24

Folder layer przygotowuje:

    topLevelTileIds
    topLevelPositions
    topLevelSizes

i dopiero przekazuje je do istniejącego resolvera.

Existing resolver pozostaje źródłem prawdy dla:

- drag
- resize
- collision
- reflow
- bounded workspace

Folder logic jest osobną warstwą.

---

# Test Matrix Folder v0.1

F01
Leaf A -> Leaf B, hover krótszy niż 650 ms.
Oczekiwane:
brak folderu, zachowanie zwykłego Tile Engine.

F02
Leaf A -> Leaf B, central target przez >= 650 ms.
Oczekiwane:
target przechodzi w FOLDER_TARGET_ARMED.

F03
Uzbrojony Leaf A -> Leaf B, drag end.
Oczekiwane:
powstaje folder z dokładnie A i B.

F04
Po utworzeniu folderu A ani B nie występują top-level.
Oczekiwane:
brak duplikacji.

F05
Nowy folder zajmuje pozycję dawnego target B.
Oczekiwane:
legalny layout.

F06
Nowy folder przyjmuje początkowy rozmiar target B.
Oczekiwane:
legalny rozmiar i brak kolizji.

F07
Leaf C -> istniejący folder, dwell >= 650 ms.
Oczekiwane:
C zostaje dodany dokładnie raz.

F08
Folder -> Leaf.
Oczekiwane:
brak folder nesting, normalny reflow.

F09
Folder A -> Folder B.
Oczekiwane:
brak merge i brak nesting.

F10
Hover target zmienia się przed 650 ms.
Oczekiwane:
timer/candidate zostaje zresetowany.

F11
Drag cancel podczas folder hover.
Oczekiwane:
pełny powrót do stanu sprzed drag.

F12
Tap folder.
Oczekiwane:
folder otwiera się i pokazuje wszystkie dzieci dokładnie raz.

F13
Tap child w folderze.
Oczekiwane:
wywołana zostaje oryginalna akcja leaf tile.

F14
Resize folderu.
Oczekiwane:
korzysta z istniejącego Tile Engine i zachowuje invarianty.

F15
Move folderu.
Oczekiwane:
korzysta z istniejącego Tile Engine i zachowuje invarianty.

F16
Restart aplikacji.
Oczekiwane:
membership, folderId, pozycje i rozmiary zostają zachowane.

F17
Grid 6 -> grid 8.
Oczekiwane:
membership bez zmian, layout/rozmiar folderu z grid 8.

F18
Grid 8 -> grid 6.
Oczekiwane:
membership bez zmian, layout/rozmiar folderu z grid 6.

F19
Rozwiąż folder przy dostępnym miejscu.
Oczekiwane:
folder znika, dzieci wracają top-level, layout jest legalny.

F20
Rozwiąż folder bez miejsca.
Oczekiwane:
cała operacja REJECT, folder pozostaje bez zmian.

F21
Persisted state: jeden leaf w dwóch folderach.
Oczekiwane:
safe fallback.

F22
Persisted state: folder zawiera folder.
Oczekiwane:
safe fallback.

F23
Persisted layout folderu zawiera kolizję.
Oczekiwane:
safe fallback.

F24
Wielokrotne create/add/move/resize/dissolve + grid switch.
Oczekiwane:
wszystkie folder invariants i Tile Engine invariants nadal PASS.

---

# Definition of Done

Metro Tile Folders v1 uznajemy za zakończone dopiero gdy:

    F01-F24 = PASS

oraz:

- T01-T24 nadal PASS
- unit tests PASS
- assembleDebug PASS
- git diff --check PASS
- restart persistence PASS
- grid 6/8 persistence PASS
- manual Android smoke PASS
- brak regresji listy plików pod dashboardem
- brak crash podczas drag/cancel/folder open
- working tree po finalnym commicie jest czysty
