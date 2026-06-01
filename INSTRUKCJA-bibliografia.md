# Instalacja pola `dc.relation.references` na działającej instalacji DSpace

Nowe pole bibliografii dodane w commicie `3fcd5056ee` (gałąź `7.6.1-submission-translation`).

## Najważniejsze: jak to repo składa formularze

W tym repozytorium formularze edytuje się w **częściach** (`submission-forms-parts/*.xml`),
a fizyczny, „sklejony" plik `submission-forms.xml` powstaje lokalnie — przez pre-commit hook
(`scripts/generate-xinclude-xml.py`, który odpala `xmllint --noent` na `submission-forms.master.xml`).

**DSpace nie zna pojęcia „parts".** W czasie działania serwer czyta **wyłącznie jeden plik**:
`$DSPACE/config/submission-forms.xml`. Na produkcji **nie ma** i **nie potrzebujesz** katalogu
`submission-forms-parts/` ani `submission-forms.master.xml`. Nie ma też żadnego pipeline'u
(Ant/Maven), który by cokolwiek sklejał na serwerze — sklejanie dzieje się tylko u developera.

Wniosek praktyczny: **na produkcję kopiujesz gotowy `submission-forms.xml`, a nie części.**

## Zmienione pliki

- `dspace/config/registries/dublin-core-types.xml` — rejestracja pola `dc.relation.references`
- `dspace/config/submission-forms.xml` — sklejony plik z polem w `traditionalpagefour` oraz `openairePublicationPagetwoForm`
- (źródła, tylko dla developera: `submission-forms-parts/form-traditional.xml`, `form-openaire.xml`)

## Procedura na działającym serwerze

### 1. Skopiuj dwa pliki do uruchomionej instalacji

```bash
DSPACE=/dspace                       # <-- popraw na właściwą ścieżkę [dspace-install]
REPO=/sciezka/do/zrodel/DSpace       # <-- popraw na ścieżkę do tego repo

# Gotowy, sklejony formularz (to czyta DSpace):
cp "$REPO/dspace/config/submission-forms.xml" \
   "$DSPACE/config/submission-forms.xml"

# Rejestr metadanych:
cp "$REPO/dspace/config/registries/dublin-core-types.xml" \
   "$DSPACE/config/registries/"
```

Sprawdź, że skopiowany plik faktycznie zawiera pole — powinny być **dwa** trafienia:

```bash
grep -c Bibliografia "$DSPACE/config/submission-forms.xml"   # oczekiwane: 2
```

> Jeśli edytowałeś części (`submission-forms-parts/*.xml`) i `submission-forms.xml` jest
> nieaktualny, zregeneruj go u siebie przed kopiowaniem:
> ```bash
> python "$REPO/scripts/generate-xinclude-xml.py"
> # lub ręcznie:
> cd "$REPO/dspace/config" && xmllint --noent --output submission-forms.xml submission-forms.master.xml
> ```

### 2. Wczytaj nowe pole do bazy

Komenda jest **idempotentna** — istniejące pola pomija, dorzuca tylko nowe (`dc.relation.references`):

```bash
"$DSPACE/bin/dspace" registry-loader -metadata "$DSPACE/config/registries/dublin-core-types.xml"
```

W Dockerze:

```bash
docker compose -p d9 -f docker-compose-cli.yml run --rm dspace-cli \
    registry-loader -metadata /dspace/config/registries/dublin-core-types.xml
```

### 3. Restart Tomcat

```bash
systemctl restart tomcat
# lub w Dockerze:
# docker compose -p d9 restart dspace
```

## Weryfikacja

1. **Admin UI → Access Control → Metadata registry → `dc`** — sprawdź, czy widać `dc.relation.references`.
2. **Nowy submission** — w formularzu na stronie 4 (sekcja z opisem) powinno pojawić się pole
   "Bibliografia" z przyciskiem "+" do dodawania kolejnych pozycji.
3. **Edycja istniejącego rekordu** — `Edit item → Metadata → Add new metadata` — pole
   `dc.relation.references` powinno być na liście.

## Co zrobić, jak coś nie zadziała

| Objaw | Najpewniejsza przyczyna |
|-------|--------------------------|
| Formularz się nie ładuje, w logach `MetadataField not found` | Krok 2 pominięty — pole nie jest zarejestrowane w bazie |
| Pole jest w rejestrze, ale nie pojawia się w formularzu | Na serwerze leży stary `submission-forms.xml` — `grep Bibliografia` daje 0; skopiuj poprawny |
| `grep Bibliografia` w repo daje 0 | Sklejony plik nieaktualny — zregeneruj (patrz ramka w kroku 1) |

Logi: `$DSPACE/log/dspace.log` (i `catalina.out` Tomcata) — szukaj słów `references` albo `MetadataField`.

## Pole jest opcjonalne

Pole nie ma znacznika `<required/>`, więc bibliografia jest **opcjonalna** — submission przejdzie
bez wypełnienia, a stare rekordy nie mają pola i nie trzeba ich uzupełniać. Gdyby kiedyś miało być
wymagane, dodaj `<required></required>` do obu pól w `submission-forms-parts/form-traditional.xml`
i `form-openaire.xml`, zregeneruj plik (`python scripts/generate-xinclude-xml.py`) i skopiuj ponownie.

## Świeża instalacja

Na nowej, pustej instalacji DSpace'a krok 2 jest **niepotrzebny** — `dublin-core-types.xml` jest
seedem ładowanym automatycznie przy pierwszym uruchomieniu. Wystarczy mieć poprawny
`submission-forms.xml` na miejscu i wystartować serwer.
