# Scot Traffic: MPD coursework (MHI326841, 2026/27)

This Android app (Java) shows the current Traffic Scotland unplanned events and roadworks from the
DATEX II feeds. Minimum SDK is 26 (Android 8.0).

## Setup
Add your keys to `local.properties` (Android Studio ▸ Gradle Scripts). That file is **not**
committed to GitHub. Write each value without quotes or spaces:
```
TS_CLIENT_ID=your-traffic-scotland-client-id
TS_CLIENT_KEY=your-traffic-scotland-client-key
MAPS_API_KEY=AIza...
```
The secrets-gradle-plugin injects these values into `AndroidManifest.xml` as `<meta-data>`, and
`data/Credentials.java` reads them back. Without them the project still builds, using the
placeholders in `local.defaults.properties`. In that case the app reports "client ID/key not set"
and the map stays grey.

## Architecture: single Activity + Fragments + MVVM
```
MainActivity (View: toolbar/menu, status banner, navigation, fragment containers)
 ├─ SituationListFragment   tab 1: list of all situations (filter: all / incidents / roadworks)
 ├─ SearchFragment          tab 2: search by road name, search by distance
 ├─ SituationMapFragment    tab 3: Google map (all / search results / one situation)
 └─ DetailsFragment         details of the selected situation
        │ observe LiveData / call methods
TrafficViewModel (ViewModel: UI state; survives rotation)
        │
TrafficRepository (Model: download, cache, parse, auto-update, threads)
 ├─ SituationParser         XmlPullParser → ArrayList<Situation>
 └─ model/                  Situation, Impact, LocationPoint, enums, TextHelper, GeoHelper
```

| Rubric item | Where |
|---|---|
| List of situations (type + generalPublicComment), selectable | `SituationListFragment`, `SituationAdapter` (RecyclerView), `item_situation.xml` |
| Details (impact, validity, locations…) | `DetailsFragment`: Where / When / Impact / Status / Other information / Record |
| Colour-coded roadworks duration | `DurationBand` (≤7 days green, ≤3 months amber, longer red), list strip and badge, map line colour, legend |
| Search by road name | `SearchFragment` + `Situation.matchesRoad()`: road numbers in any format (`m 8`, `A74(M)`), place names, autocomplete from the roads in the data |
| Search by distance | `SearchFragment`: coarse location (runtime permission) or fixed GCU coordinates; 1–100 km slider; results sorted by distance, with the distance shown |
| Icons (≥6 types) | `res/drawable/ic_kind_*.xml`: 9 original vector icons (road closed, lane restriction, queue, accident, obstruction, weather, roadworks, urgent roadworks, other) |
| Map of all/selected situations | `SituationMapFragment`: icon marker and coloured from→to line per situation; all, one, or **multiple user-selected** (search results) |
| PullParser + further parsing | `SituationParser` (element path stack, attributes `xsi:type`/`id`/`version`, descriptors). `TextHelper`: dates, road numbers, enum codes, roadworks "location – works" split, abbreviations (TTL, MVMS, MLC…) |
| Data structures | `ArrayList<Situation>`; each has an `Impact` object, a `List<LocationPoint>` (with `Map` of descriptors and `Set` of roads), and a `Map` of other fields |
| Portrait / landscape | `layout/activity_main.xml` (bottom navigation, details over the list) vs `layout-land/activity_main.xml` (navigation rail, list and details side by side); state kept in the ViewModel |
| Threading (no runOnUiThread) | `TrafficRepository`: `ScheduledExecutorService` + 2-thread `ExecutorService` (both feeds in parallel), results via `LiveData.postValue()` |
| Auto update | Runs at start-up and then every 60 / 15 / 5 / 1 minutes (menu ▸ Auto-update interval, saved in SharedPreferences) |
| Offline | Last good download cached in internal storage; banner shows offline/error state; automatic refresh when the connection returns (`NetworkCallback`) |

## Tests
`app/src/test`: JUnit tests that run the real parser on the sample feeds supplied with the
coursework (`app/src/test/resources`). They check record counts against the XML, field values,
delays, locations, road/distance search, and robustness to unknown elements.
`MainActivityUiTest` (Robolectric, test-only) launches the real app with the sample data saved as
offline data. It walks through list → details → filter → rotation → road search → distance
search → map, and saves screenshots to `app/build/ui-screenshots/`. Run them with
*Gradle ▸ app ▸ verification ▸ testDebugUnitTest*, or right-click `app/src/test/java` ▸ Run tests.

## Data source
Contains Traffic Scotland data (Transport Scotland DATEX II feeds). The icons are original
drawings made for this app.
