# RouterCloud Metro Tile Engine - Behavior Specification v0.1

Status: implementation target

## 1. Zakres

Specyfikacja obejmuje:

- dashboard kafelkow RouterCloud
- drag and drop
- resize
- kolizje
- reflow
- persistence
- siatke 6 i 8 jednostek

Poza zakresem:

- foldery kafelkow
- grupy kafelkow
- nowe typy kafelkow
- Live Tiles
- nowe funkcje dashboardu

Nie dodajemy nowych funkcji, dopoki obecny Tile Engine nie zostanie ukonczony i przetestowany.

---

## 2. Cel

Mechanika kafelkow ma byc inspirowana ekranem Start Windows Phone / Windows 10 Mobile tam, gdzie ma to sens dla RouterCloud.

RouterCloud nie jest jednak pelnoekranowym launcherem.

Pod dashboardem znajduje sie lista plikow i katalogow.

Dlatego przestrzen kafelkow jest ograniczonym workspace i przeciaganie kafelkow nie moze dowolnie zwiekszac wysokosci dashboardu.

---

## 3. Twarde invarianty

Kazdy zaakceptowany stan layoutu MUSI spelniac wszystkie cztery warunki.

### Invariant 1 - brak nakladania

Zadne dwa kafelki nigdy sie nie nakladaja.

### Invariant 2 - granice workspace

Zaden kafelek nie moze wyjsc poza granice workspace.

Warunki:

    column >= 0
    row >= 0
    column + widthUnits <= gridUnits
    row + heightUnits <= workspaceRows

### Invariant 3 - stala wysokosc dashboardu podczas drag

Drag nie moze zwiekszac wysokosci dashboardu.

Drag nie moze spychac listy plikow i katalogow w dol.

### Invariant 4 - operacje atomowe

Kazda operacja daje tylko jeden z dwoch wynikow:

    ACCEPT - caly nowy layout jest poprawny

albo:

    REJECT - pozostaje poprzedni poprawny layout

Nie istnieje legalny stan posredni zawierajacy kolizje.

---

## 4. Geometria

### gridUnits

Tryb standardowy:

    6 jednostek szerokosci

Tryb Pokaz wiecej kafelkow:

    8 jednostek szerokosci

### workspaceRows

Na obecnym etapie:

    workspaceRows = 4

Drag nie moze zwiekszac workspaceRows.

Jezeli operacja wymagalaby wyjscia ponizej czwartego wiersza, operacja jest odrzucana.

Dzieki temu lista plikow pod dashboardem pozostaje na stalej pozycji.

---

## 5. Rozmiary kafelkow

Small:

    1 x 1
    standard W10M

Medium:

    2 x 2
    standard W10M

Wide:

    4 x 2
    standard W10M

Large:

    4 x 4
    rozszerzenie RouterCloud

Large nie jest traktowany jako standardowy finalny rozmiar Windows 10 Mobile.

Jest swiadomym rozszerzeniem RouterCloud.

Cykl resize:

    Small -> Medium -> Wide -> Large -> Small

---

## 6. Model pozycji

Kazdy kafelek posiada stan:

    tileId
    column
    row
    size

Kolejnosc kafelkow w liscie nie definiuje ich pozycji wizualnej.

Pozycje sa definiowane przez:

    column
    row

Puste miejsca w siatce sa dozwolone.

---

## 7. Tryb edycji

Wejscie w tryb edycji:

- dlugie przytrzymanie kafelka

Po wejsciu:

- kafelek zostaje zaznaczony
- pojawia sie kontrolka resize
- kafelek mozna przeciagac

Wyjscie:

- tap w pusta przestrzen dashboardu

Nie uzywamy osobnego przycisku Gotowe.

---

## 8. Drag - start

Przy rozpoczeciu drag zapisywane sa:

    dragStartLayout
    draggingTileId
    dragStartPosition

dragStartLayout oznacza ostatni poprawny layout przed rozpoczeciem gestu.

---

## 9. Drag - snap to grid

Pozycja przeciaganego kafelka jest przeliczana na najblizsze pole siatki.

Kafelki zajmuja tylko pelne komorki.

Nie istnieja pozycje czesciowe.

---

## 10. Drag na puste miejsce

Jezeli docelowy obszar jest pusty:

    movingTile -> target

Pozostale kafelki nie zmieniaja pozycji.

---

## 11. Drag na zajete miejsce

Przeciagany kafelek ma pierwszenstwo.

Jezeli target jest zajety:

    movingTile -> target

Kafelki kolidujace zostaja oznaczone jako displaced.

Silnik musi sprobowac zrobic im miejsce.

Nie blokujemy ruchu tylko dlatego, ze target byl wczesniej zajety.

---

## 12. Reflow - priorytet A

Pierwsza proba dla displaced tile:

    poprzednia pozycja przeciaganego kafelka

Jezeli displaced tile miesci sie tam bez kolizji, zajmuje to miejsce.

Daje to naturalna zamiane dwoch kafelkow o zgodnych rozmiarach.

Przyklad:

    BEFORE

    A B

    drag B -> A

    AFTER

    B A

---

## 13. Reflow - priorytet B

Jezeli naturalny swap nie jest mozliwy, silnik szuka najblizszego legalnego miejsca.

Odleglosc:

    abs(column - preferredColumn)
    +
    abs(row - preferredRow)

Przy remisie:

1. mniejszy row
2. mniejszy column

Resolver musi byc deterministyczny.

Ten sam input zawsze musi dawac ten sam output.

---

## 14. Reflow - priorytet C

Jezeli nie da sie umiescic wszystkich kafelkow:

    REJECT

Caly layout pozostaje w poprzednim poprawnym stanie.

Nie wolno zatwierdzac czesciowego reflow.

---

## 15. Wiele kolizji

Wide lub Large moze wejsc jednoczesnie na wiecej niz jeden kafelek.

Wszystkie kolizje sa traktowane jako jedna transakcja.

Nie wolno:

- przesunac jednego kafelka
- zatwierdzic layoutu
- dopiero potem naprawiac kolejnego

Najpierw musi powstac kompletny kandydat layoutu.

Dopiero potem:

    validateLayout

Jezeli walidacja przejdzie:

    ACCEPT

W przeciwnym razie:

    REJECT

---

## 16. Live reflow podczas drag

Podczas drag wykonywane sa kolejne kroki:

1. obliczenie target
2. resolveLayoutChange
3. validateLayout
4. jezeli PASS - pokazanie nowego preview
5. jezeli FAIL - zachowanie ostatniego poprawnego preview

Persistence nie jest zapisywane przy kazdym ruchu palca.

Zapis nastepuje dopiero po poprawnym drop.

---

## 17. Drop

Jezeli aktualny preview jest poprawny:

    currentLayout = previewLayout
    persist currentLayout

Jezeli nie istnieje poprawny preview:

    currentLayout = dragStartLayout

---

## 18. Drag cancel

Przerwanie gestu zawsze przywraca:

    currentLayout = dragStartLayout

Nie zapisujemy zmian.

---

## 19. Resize

Resize i drag MUSZA korzystac z tego samego resolvera.

Nie tworzymy osobnego systemu kolizji dla resize.

Resize przekazuje do resolvera:

    tileId
    currentPosition
    nextSize

Domyslny anchor:

    top-left

Kafelek najpierw probuje zachowac swoje column i row.

---

## 20. Resize bez kolizji

Jezeli nowy rozmiar miesci sie w obecnej pozycji:

    ACCEPT

Pozostale kafelki pozostaja bez zmian.

---

## 21. Resize z kolizja

Jezeli powiekszony kafelek nachodzi na inne:

- resized tile ma pierwszenstwo
- kolidujace kafelki przechodza przez ten sam reflow
- caly wynik musi przejsc validateLayout

Jezeli nie ma miejsca:

    REJECT resize

Rozmiar pozostaje bez zmian.

---

## 22. Dolna granica workspace

Operacje spelniajace warunek:

    row + heightUnits > workspaceRows

sa zabronione.

Przeciaganie kafelka nizej:

- nie tworzy nowych wierszy
- nie zwieksza dashboardu
- nie przesuwa listy plikow
- nie zapisuje nielegalnej pozycji

---

## 23. Persistence

Osobny layout przechowujemy dla:

    gridUnits = 6

oraz:

    gridUnits = 8

Po restarcie aplikacji:

- pozycje musza zostac zachowane
- rozmiary musza zostac zachowane
- zapisany layout musi przejsc validateLayout

Jezeli zapisany layout jest niepoprawny:

- nie renderujemy kolizji
- uruchamiamy bezpieczny layout domyslny

---

## 24. Foldery

Foldery kafelkow sa poza zakresem projektu na obecnym etapie.

Przeciagniecie kafelka na kafelek oznacza:

    reflow

Nie oznacza:

    create folder

Do folderow nie wracamy przed ukonczeniem i przetestowaniem obecnego Tile Engine.

---

# Test Matrix v0.1

T01
Medium -> wolne pole
Oczekiwane: kafelek zostaje dokladnie w target.

T02
Medium A -> pozycja Medium B
Oczekiwane: B robi miejsce lub nastepuje naturalny swap.

T03
Dolny Medium -> gorny zajety slot
Oczekiwane: istniejacy kafelek zostaje przesuniety.

T04
Dwa Medium pionowo
Oczekiwane: oba moga pozostac jeden pod drugim.

T05
Dwa Medium poziomo
Oczekiwane: oba moga pozostac obok siebie.

T06
Large + dwa Medium pionowo w grid 8
Oczekiwane: legalny layout.

T07
Large nachodzi na dwa Medium
Oczekiwane: oba kafelki sa displaced w jednej transakcji.

T08
Drag na czesciowo zajety obszar
Oczekiwane: pelny resolver kolizji.

T09
Drag poza lewa granice
Oczekiwane: brak nielegalnej pozycji.

T10
Drag poza prawa granice
Oczekiwane: brak nielegalnej pozycji.

T11
Drag powyzej workspace
Oczekiwane: brak nielegalnej pozycji.

T12
Drag ponizej row 3
Oczekiwane: dashboard nie rosnie.

T13
Drag ponizej workspace
Oczekiwane: lista plikow nie przesuwa sie.

T14
Brak miejsca na reflow
Oczekiwane: caly ruch REJECT.

T15
Resize bez kolizji
Oczekiwane: rozmiar zostaje zmieniony.

T16
Resize powoduje jedna kolizje
Oczekiwane: sasiad robi miejsce.

T17
Resize powoduje wiele kolizji
Oczekiwane: atomowy reflow.

T18
Resize nie miesci sie
Oczekiwane: resize REJECT.

T19
Drag cancel
Oczekiwane: pelny powrot do dragStartLayout.

T20
Restart aplikacji
Oczekiwane: pozycje i rozmiary zachowane.

T21
Grid 6 -> grid 8
Oczekiwane: aktywowany zapisany layout dla 8 jednostek.

T22
Grid 8 -> grid 6
Oczekiwane: aktywowany zapisany layout dla 6 jednostek.

T23
Zapisany layout zawiera kolizje
Oczekiwane: bezpieczny fallback zamiast renderowania kolizji.

T24
Wielokrotne drag i resize
Oczekiwane: wszystkie invarianty nadal PASS.

---

# Definition of Done

Tile Engine uznajemy za ukonczony dopiero wtedy, gdy:

    T01-T24 = PASS

oraz po kazdej zaakceptowanej operacji:

    validateLayout = PASS

Do tego momentu nie dodajemy nowych funkcji dashboardu.
