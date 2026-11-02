# Destroy Extras

Недостающие химические машины Destroy для dev-задачи #22. Игровой релиз **0.2.0** содержит мембранный электролизёр M2,
цепочку HCl/FeCl₃ M3, травильную ванну L1 M4, промышленную форму с Create-узлами M5 и Survival-маршрут M6.

## Архитектура

- Destroy остаётся источником молекул, смесей, реакций и существующих машин.
- Destroy Bridge предоставляет TFC fluid adapter, EE process driver и атомарную многопортовую операцию.
- Destroy Extras владеет production-блоком, мембраной, рецептом и JEI-категорией электролизёра.
- Платы и их редактор остаются в EE Extras.
- Отдельные fluid type для H₂, Cl₂ и NaOH не создаются: используются `destroy:gas` и `destroy:mixture` с точными
  data components Destroy. В Destroy 0.4.6-eligibbs газовая смесь зарегистрирована именно как `destroy:gas`
  (`DestroyFluids.GAS_MIXTURE`).

## M2: мембранный электролизёр

- Блок `destroy_extras:membrane_electrolyzer` и сменная мембрана `destroy_extras:membrane` на 64 партии.
  Полностью изношенная мембрана остаётся в слоте и должна быть заменена.
- Питание EE: **300 В / 8 А**, сопротивление 37,5 Ом, рабочая зона 270–330 В.
- Входной TFC-бак 8000 мБ, внутренняя Destroy-смесь 2000 мБ и три выхода по 4000 мБ.
- Порты: север — H₂, юг — Cl₂, восток — NaOH, запад и остальные стороны — вход; item handler открывает слот мембраны.
- Статусы `IDLE`, `WAITING_FOR_POWER`, `RUNNING`, `BLOCKED_OUTPUT`, `FAULT` имеют русские и английские переводы.
- Отсутствующая или изношенная мембрана даёт `FAULT`; переполнение любого выхода блокирует всю партию без частичной
  выдачи. Прогресс сохраняется при просадке питания.
- Баки, компоненты смесей, мембрана, прогресс, число партий и id рецепта сохраняются в NBT. После перезагрузки рецепт
  восстанавливается по id либо по сохранённой смеси; смена `RecipeManager` сбрасывает кэш.

Рецепт `destroy_extras:electrolysis/salt_water` загружается из datapack через узкий тип
`destroy_extras:electrolysis`: штатный `destroy:electrolysis` допускает только два жидких выхода. JSON задаёт 1000 мБ
TFC salt water, концентрации соли 1 и воды 10, длительность 200 тиков и продукты 250 мБ H₂ + 250 мБ Cl₂ + 500 мБ NaOH.
Концентрации не зашиты в production-код.

JEI-категория находится в отдельном `compat/jei`; JEI объявлен необязательной клиентской зависимостью. Категории
электролиза и травления также доступны через EMI/Jemi. Реальный клиент, обе интеграции и Ponder проверены в M5.

## M3: HCl и FeCl₃

- Дополнительные машины не добавлены: штатный незеритовый Destroy Vat 3×3×3 на 27 000 мБ выполняет UV-синтез HCl и
  растворение железной пыли сразу до Fe(III), отдельный FeCl₂-этап не нужен.
- Для синтеза используются реальные H₂/Cl₂ из JSON M2. Нужен UV и регулируемое охлаждение: постоянный cooler
  конденсирует газы, а без отвода экзотермической теплоты давление разрушает Vat.
- Поглощение выполняет атомарный `HydrochloricAcidAdapter` из Bridge: чистые HCl + H₂O → H⁺ + Cl⁻ + H₂O с сохранением
  молей и объёма. Свободное смешивание в Vat не используется: в Destroy 0.4.6-eligibbs оно нарушает баланс вещества.
  Эту же операцию должна вызывать травильная ванна M4.
- Новых fluid type, молекул и release-машин не появилось; стенд и термостат находятся только в devtest.

Команды стенда:

```bash
./test-server.sh 'destroyextratest m3build' 'destroyextratest m3prepare hcl' \
  'sleep 30' 'destroyextratest m3assert hcl'
./test-server.sh 'destroyextratest m3build' 'destroyextratest m3prepare hcl_no_uv' \
  'sleep 2' 'destroyextratest m3assert hcl_no_uv'
./test-server.sh 'destroyextratest m3build' 'destroyextratest m3prepare absorption' \
  'destroyextratest m3assert absorption'
./test-server.sh 'destroyextratest m3build' 'destroyextratest m3prepare iron' \
  'sleep 2' 'destroyextratest m3assert iron'
```

Фактические результаты: Vat и обе реакции — `PASS 4/4`; HCl — `PASS 4/4`, расход H₂/Cl₂ и выдача HCl соответствуют
1:1:2, жидкой фазы нет; без UV — `PASS 2/2`, HCl не образуется. Атомарное поглощение 25 мБ HCl + 250 мБ воды —
`PASS 3/3`: 275 мБ смеси, по 1021,6676 моля H⁺/Cl⁻ и 13873,4736 моля H₂O. Железо — `PASS 5/5`: одна пыль
израсходована, образованы 8,7546 Fe(III) вместо 9 и 13,5 H₂, израсходованы 27,6272 H⁺ вместо 27; расхождение Fe/H⁺
до 3 % — численная погрешность свободной реакции Destroy, Fe(II) не образуется.

## M4: травильная ванна L1

- `destroy_extras:etching_bath` работает без EE/FE. Узкий JSON-тип `destroy_extras:etching` принимает заготовку, маску и
  одну Destroy-смесь; отдельная JEI-категория показывает параметры и показательный отработанный раствор.
- Рецепт L1: 250 мБ `destroy:mixture`, 400 тиков, 3 моля меди. Химия выполняется над фактическими data components:
  Cu + 2 Fe(III) → Cu(II) + 2 Fe(II), то есть расходуются 6 Fe(III), образуются 3 Cu(II) и 6 Fe(II). Объём,
  температура, вода, хлориды и остальные компоненты сохраняются. Новые fluid type и молекулы не добавляются.
- Кислотный раствор готовится только через `HydrochloricAcidAdapter`; ванна не возвращает его в свободную симуляцию
  Vat. Списание входа и заполнение выхода выполняет атомарная Bridge-операция после проверки места под плату и раствор.
- Настоящие предметы `ee_extras:l1_board_blank`, `l1_mask`, `l1_board` и компонент `ee_extras:pcb_topology` находятся в
  EE Extras. Плата создаётся через `transmuteCopy` фактической маски, поэтому топология сохраняется точно.
- Север — выход отработанного раствора, юг — чистый HCl, восток — вода, остальные стороны — рабочий раствор FeCl₃;
  item handler открывает два входа и готовую плату.
  Смена входов сбрасывает прогресс. Баки, предметы, рецепт и прогресс сохраняются в NBT и в `BlockEntityData` выпавшей
  ванны; повторная установка восстанавливает их штатным путём.

Проверки на dedicated server: окружение `PASS 6/6`; циклы devtest и настоящей L1 — по `PASS 10/10`, 250 мБ раствора,
3 Cu(II), 6 Fe(II), точная топология; переполненный выход — `PASS 3/3`, отсутствие Fe(III) — `PASS 2/2`; разрушение и
повторная установка — `PASS 6/6`; перезапуск на 100 тиках — `PASS 5/5`, затем завершение L1 — `PASS 10/10`. Регрессия
M2 и всех сценариев M3 проходит. Ресурсы, модели и реальный клиент проверены.

```bash
./test-server.sh 'destroyextratest m4build' 'destroyextratest m4acid' \
  'sleep 2' 'destroyextratest m4load l1' 'sleep 21' 'destroyextratest m4assert l1'
```

## M5: промышленная форма и Create-узлы

- Электролизёр собирается в корпус 3×3×2 из баков и медного корпуса Create. Контроллер и настоящий mechanical pump
  находятся в медной перегородке; для работы насосу нужно не менее 16 об/мин. Без собранного корпуса или достаточного
  вращения партия не идёт. Проверки структуры и поиска рецепта выполняются раз в 20 тиков, а не на каждом тике.
- Инженерные очки показывают состояние, питание, корпус и скорость насоса. Разрушение и восстановление корпуса
  останавливает и возобновляет работу без потери вещества. Выпавший контроллер сохраняет `BlockEntityData`: баки,
  мембрану, прогресс и рецепт. Направления capability-портов M2 не изменились.
- Мешалка Create над травильной ванной при скорости от 16 об/мин сокращает цикл L1 с 400 до 200 тиков. Химия M2–M4,
  мольный баланс, набор молекул и fluid type не менялись; HCl по-прежнему поглощается только Bridge-операцией.
- Добавлены русские и английские подсказки и Ponder-сцена промышленной сборки. Клиентский стенд использует JEI
  19.56.0.441, совместимый с Petrolpark 1.5.11; несовместимый JEI 19.44.0.401 из пакета в стенд не входит.

Проверки M5:

- Корпус и запуск — `PASS 4/4`; границы RPM — `PASS 4/4`; разрушение/восстановление корпуса — `PASS 3/3`;
  разрушение контроллера и повторная установка — `PASS 6/6`. Минимальный стенд с production-партией — `PASS 3/3`.
- `PROFILE=thermal`: M5 — `PASS 3/3`, смешанный цикл травления — `PASS 11/11`, 200 тиков, 250 мБ отработанного
  раствора, Cu(II)=3, Fe(II)=6, Fe(III)=1,8200278282165527.
- `PROFILE=sable`: сборка области командой `/sable assemble area` — `PASS 6/6`. После переноса на подуровень сохраняются
  EE, насос 64 об/мин, баки, мембрана и производство новой партии. `shatter` разбивает подуровень на новые подуровни,
  а не возвращает исходную наземную форму, поэтому отдельной проверкой обратной установки он не является.
- Полный пакет: 230 модов на NeoForge 21.1.251, загрузка за 121 с; KubeJS startup 6/6 и server 84/84, ошибок скриптов
  нет. Оставшиеся ошибки recipe/loot/client-class воспроизводятся как исходные проблемы пакета и не относятся к M5.
- Реальный клиент: `DESTROYEXTRACLIENT PASS`. JEI и EMI/Jemi открывают обе категории с рецептами электролиза и
  травления; Ponder открывается с русскими строками. Скриншоты сохраняются в `run-client/screenshots/`.

### Нагрузка 8 и 32 установок

Каждый прогон состоит из трёх отрезков по 200 с с двумя полными перезапусками. Используются production-контроллеры,
реальные Create input pumps и EE-цепи; выходы забираются через capabilities в большие NBT-persistent стоки devtest.

| Установок |   Время |    TPS |    MSPT mean / p50 / p95 |       Партии | Sync |    NBT payload |
| --------: | ------: | -----: | -----------------------: | -----------: | ---: | -------------: |
|         8 | 600,1 с | 20,018 | 0,637 / 0,345 / 1,453 мс |  472 (по 59) |  968 |   641 400 байт |
|        32 | 600,1 с | 20,008 | 1,027 / 0,512 / 2,247 мс | 1920 (по 60) | 3840 | 2 544 160 байт |

Средний размер update tag — 662,6 и 662,5 байта соответственно; минимум 299,752 В, максимум 7,993 А в обоих прогонах.
Замен мембран не потребовалось; дозаправки источника — 72 и 309. `sync` здесь — серверные переходы статуса, а байты —
размер NBT update tag, не измеренный сетевой трафик клиентов: стенд работает через RCON без игроков.

```bash
./test-server.sh 'destroyextratest m5build' 'destroyextratest m5rpm 16' \
  'sleep 2' 'destroyextratest m5assert running'
PROFILE=thermal ./test-server.sh 'destroyextratest m5build' 'sleep 2' 'destroyextratest m5assert running'
./test-server.sh 'destroyextratest m5load start 8' 'sleep 200' 'destroyextratest m5load report 8'
KEEP=1 ./test-server.sh 'destroyextratest m5load resume 8' 'sleep 200' 'destroyextratest m5load report 8'
KEEP=1 ./test-server.sh 'destroyextratest m5load resume 8' 'sleep 200' 'destroyextratest m5load report 8'
./test-client.sh
```

## M6: Survival-маршрут и выпуск

Сайтовый datapack `Destroy — Survival-цепочка M6` содержит пять рецептов: электролизёр из графитовых электродов IE,
соединителя EE, медных корпусов и электронной лампы Create; мембрана прессованием пеньковой ткани IE; ванна из медных
пластин, баков и медного корпуса Create; заготовка L1 прессованием фибровой плиты IE с медной пластиной; маска L1 из
бумаги и чёрного красителя. Доступная игроку цепочка: солёная вода → H₂/Cl₂ → HCl в Destroy Vat → растворение железной
пыли до Fe(III) → травление заготовки с маской.

Ванна принимает чистый HCl и воду через отдельные capability-баки. Каждый тик атомарная Bridge-операция превращает
25 мБ HCl + 250 мБ воды в 275 мБ Destroy-смеси; свободный Vat для поглощения не используется. Обычная
`minecraft:water` нормализуется в воду Destroy. Старые Input/Spent, NBT, предметы и топология плат сохраняются.

Проверки: рецепты `6/6`, поглощение `6/6`, миграция настоящего мира M2 `6/6`, мира M4 `5/5`; регресс M2–M5,
minimal/thermal/sable, реальный клиент и полный пакет на NeoForge 21.1.251 прошли. В полном пакете 230 модов,
KubeJS startup `6/6`, server `84/84`, datapack загружен. Аудит пяти новых рецептов не выявил конфликтов среди
5389 shaped, 128 pressing, 73 compacting и 1994 shapeless рецептов.

## Сборка и проверка

```bash
export JAVA_HOME="$HOME/.gradle/local-tools/jdk21/Contents/Home"
./gradlew build --console=plain
./test-server.sh 'destroyextratest environment'
```

`test-server.sh` использует production-блок, конечный TFC-бак, настоящую creative battery EE и провода. Devtest-мод и
его блоки не попадают в release jar. Поддерживаются `KEEP=1`, `RCON_PORT` и `SERVER_PORT`; логи — `run/check.log`,
ответы — `run/results.txt`.

Команды:

- `/destroyextratest environment` — загрузка и параметры JSON-рецепта;
- `/destroyextratest build` — собрать стенд M2;
- `/destroyextratest power <V>` — установить напряжение батареи;
- `/destroyextratest membrane remove|<damage>` — удалить мембрану или установить износ 0–64;
- `/destroyextratest fill hydrogen|chlorine|sodium_hydroxide` — заполнить один выход;
- `/destroyextratest restartcase` — сохранить контрольное состояние незавершённой партии и отключить питание;
- `/destroyextratest assert ports|normal|no_membrane|worn_membrane|undervoltage|blocked_hydrogen|blocked_chlorine|blocked_sodium_hydroxide|persisted`;
- `/destroyextratest fail` — намеренно падающая проверка самого стенда.

Нормальная партия:

```bash
./test-server.sh \
  'destroyextratest build' 'destroyextratest assert ports' \
  'destroyextratest power 300' 'sleep 12' \
  'destroyextratest report' 'destroyextratest assert normal'
```

Перезапуск:

```bash
./test-server.sh \
  'destroyextratest build' 'destroyextratest power 300' \
  'sleep 2' 'destroyextratest restartcase'
KEEP=1 ./test-server.sh 'destroyextratest assert persisted'
```

## Фактические проверки M2

- `./gradlew build --console=plain` — успешно; `resources_check` — ошибок нет (1 блок/состояние, 2 предмета,
  3 модели). Модели блока и мембраны отрендерены, все текстуры найдены.
- Окружение и JSON-рецепт: `PASS 4/4`; capability-порты: `PASS 5/5`.
- Норма: **299,827 В, 7,995 А**, `PASS 13/13`; одна партия, точные fluid id/data components всех продуктов,
  мембрана 63/64 и остаток 7000 мБ из исходных 8000 мБ.
- Без мембраны и при износе 64/64: `FAULT`, нет прогресса и продуктов, вход сохранён; `PASS 3/3` в каждом сценарии.
- Просадка до 200 В после незавершённого прогресса: **199,885 В, 5,330 А**, `WAITING_FOR_POWER`, не более одного
  переходного тика, продуктов нет; `PASS 6/6`.
- Переполнение каждого выхода: `BLOCKED_OUTPUT`, другие выходы пусты, вход и прогресс сохранены; `PASS 6/6` в каждом
  сценарии.
- Полный перезапуск через `KEEP=1`: прогресс, баки, точный NBT смеси, износ и id рецепта восстановлены без повторной
  выдачи; `PASS 6/6`.
- `/destroyextratest fail` даёт `FAIL 0/1` и ненулевой код стенда.

Клиент/JEI/EMI, полный пакет и нагрузка проверены в M5; Survival-аудит, миграция и выпуск завершены в M6.
