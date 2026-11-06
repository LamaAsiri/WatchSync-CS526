# WatchSync — CS 526 Term Project

تطبيق Android (Java + XML) زي **TV Time**: كل مستخدم يتابع مسلسلاته وأفلامه ويعلّم الحلقات اللي شافها،
وفوقها: قروبات، تصويت على وش تشوفون، جلسات مشاهدة، وحماية من الحرق.

---

## خطوات التشغيل (أول مرة)

### 1. افتحي المشروع
Android Studio ← **File ← Open** ← اختاري مجلد `WatchSync` كامل ← انتظري Gradle Sync.

> المشروع ما يشتغل قبل الخطوة 2، لأنه يحتاج ملف Firebase حقكم.

### 2. سوّي مشروع Firebase (وحدة بس من الفريق، والباقي ياخذون نفس الملف)
1. ادخلي [console.firebase.google.com](https://console.firebase.google.com) ← **Create a project** ← سميه `WatchSync` (تقدرين تقفلين Google Analytics).
2. من القائمة: **Build ← Authentication ← Get started ← Email/Password ← Enable ← Save**.
3. من القائمة: **Build ← Realtime Database ← Create Database** ← اختاري أي موقع ← **Start in locked mode**.
4. في صفحة Realtime Database افتحي تبويب **Rules**، امسحي اللي فيه، والصقي محتوى ملف `database.rules.json` (موجود في المشروع) ← **Publish**.
5. ارجعي لـ **Project settings** (أيقونة الترس) ← **Add app ← Android**:
   - Android package name: `com.cs526.watchsync` (لازم يكون نفسه بالضبط)
   - ← Register app ← **Download google-services.json**
6. حطي `google-services.json` داخل مجلد **`WatchSync/app/`** (جنب `build.gradle.kts`).

> مهم: نزّلي `google-services.json` **بعد** ما تسوين الـ Realtime Database (خطوة 3)، لأن الملف لازم يحتوي رابط قاعدة البيانات.
> لو نزلتيه قبل، نزليه مرة ثانية من Project settings.

### 3. مفتاح TMDB
موجود جاهز في ملف `secrets.properties` في المجلد الرئيسي. هذا الملف ما ينرفع على GitHub،
فأرسليه للبنات بالخاص وكل وحدة تحطه في نفس المكان.

### 4. شغّلي على جوالك (أخف من الـ Emulator)
1. في الجوال: Settings ← About phone ← اضغطي **Build number** 7 مرات.
2. Settings ← Developer options ← فعّلي **USB debugging**.
3. وصّلي الجوال بالكيبل ← اختاريه فوق في Android Studio ← اضغطي ▶ Run.

---

## كيف تجربين التطبيق كامل
1. سوي حساب (Sign up) ← تطلع لك Home فاضية.
2. **My Shows** ← زر البحث ← ابحثي عن مسلسل ← **+ Add** ← افتحيه وعلّمي حلقات.
3. **Groups ← Create** ← خذي الكود.
4. سوي حساب ثاني (من جوال ثاني، أو Logout وسوي حساب جديد) ← **Groups ← Join** بالكود.
5. في القروب: **Watchlist** ← أضيفوا مسلسلين أو أكثر ← الأدمن يضغط **Start a vote**.
6. صوتوا ← الأدمن **End vote** ← الفائز يصير **Now Watching**.
7. **Watch Parties ← Schedule party** ← اختاري حلقة ووقت.
8. افتحي الجلسة: تشوفين العد التنازلي، وتحذير الحرق لو أنتِ متأخرة، ومين جاهز ومين متأخر.
9. **My Progress** (Spoiler radar): مين قدامك ويقدر يحرق عليك، ومين وراك.

---

## منطق التطبيق
- كل مستخدم له **My Shows** وتقدمه الخاص (مثل TV Time). التقدم واحد في كل القروبات.
- الحلقات تنحسب كرقم واحد متسلسل عبر كل المواسم (S2 E3 = حلقة 27 لو الموسم الأول 24 حلقة).
- كل قروب يشوف **مسلسل واحد** بنفس الوقت (Now Watching).
- حالات المسلسل في القروب: Watchlist ← In vote ← Up next ← Now watching ← Finished.
- **الأدمن** (اللي أنشأ القروب) بس: يبدأ وينهي التصويت، يعلّم المسلسل خلص، يحذف أي جلسة.
- الفائز بالتصويت يصير Now Watching لو القروب فاضي، وإلا Up next.
- جلسة على حلقة N ← لازم تكونين شايفة لين N-1. الحلقات الباقية = (N-1) − حلقتك.
- بعد ما تخلص الجلسة، التطبيق يسأل الحضور "شفتوها؟" ← نعم = تقدمك يصير N.
- الفلم = حلقة وحدة (شفته / ما شفته).
- لو الأدمن طلع، أقدم عضو يصير أدمن. آخر واحد يطلع ← القروب ينحذف.

## قاعدة البيانات (Firebase Realtime Database)
```
users/{uid}                 name, email, color
users/{uid}/groups/{code}   true
users/{uid}/shows/{tmdbId}  title, posterPath, mediaType, totalEpisodes, seasonCounts, progress ...

groups/{code}               name, adminUid, createdAt
  members/{uid}             name, color, joinedAt
  watchlist/{tmdbId}        show + addedByName
  nowWatching               show
  upNext                    show
  finished/{tmdbId}         show
  vote                      open, options/{tmdbId}, ballots/{uid}, winnerTitle
  parties/{id}              tmdbId, showTitle, episode, time, note, createdBy, rsvp/{uid}, checkedIn/{uid}
```

## SQLite (على الجوال)
جدول `my_shows`: نسخة من My Shows عشان الشاشات تفتح بسرعة وتشتغل بدون نت.

## SharedPreferences
اسم المستخدم ولونه، وإعداد تذكير الجلسات.

## AlarmManager + BroadcastReceiver
تذكير قبل الجلسة بساعة (`reminders/ReminderScheduler` + `PartyReminderReceiver`).

---

## توزيع الملفات على الفريق
| الجزء | المجلد / الملفات |
|---|---|
| 1. UI & Navigation (ريماس) | `auth/` (Splash, Welcome, Login, SignUp, ForgotPassword) · `main/` (MainActivity, HomeFragment, ProfileFragment, EditProfile) · `res/values/` الثيم والألوان |
| 2. Groups & Database (جوري) | `groups/` (CreateGroup, JoinGroup, GroupActivity, Members) · `data/Db.java` · `database.rules.json` |
| 3. Shows & Voting (لمى) | `shows/` (Search, ShowDetail, ShowAdapter, EpisodeAdapter) · `main/MyShowsFragment` · `groups/` (GroupWatchlist, StartVote, Vote) · `data/TmdbApi.java` |
| 4. Watch Parties & Spoilers (لمى عسيري) | `parties/` (Parties, ScheduleParty, PartyDetail, SpoilerRadar, PartyAdapter) · `reminders/` · `util/EpisodeMath.java` |
| مشترك | `model/`, `util/`, `data/LocalDb.java`, `data/Prefs.java`, `data/ProgressStore.java` |

This product uses the TMDB API but is not endorsed or certified by TMDB.
