# Destroy Bridge

Ранний прототип моста Destroy ↔ TFC / Create / Electro Energetics для dev-задачи #22. **Это не выпуск для игроков:**
Survival-блоков, предметов и рецептов здесь пока нет. `destroy_extras` по-прежнему является отдельным аддоном будущих
машин; платы остаются в EE Extras.

## Что уже реализовано (M0 + M1)

- Воспроизводимый dedicated server на JDK 21, NeoForge 21.1.251, Create 6.0.10, EE 1.1.2, TFC 4.2.9,
  Destroy 0.4.6-eligibbs и Petrolpark 1.5.11.
- Datapack-реакция Destroy загружается через штатный `destroy/reactions`; штатный Castner не удаляется.
- `ProcessPowerDevice`: двухполюсная нагрузка EE **300 В / 8 А**, сопротивление 37,5 Ом, рабочая зона 270–330 В.
  Основной поток задаёт активность; поток симуляции только строит цепь и публикует измеренные V/A. Обращений к миру,
  блокам и рецептам из него нет.
- `SaltWaterAdapter`: принимает только `tfc:salt_water`, преобразует количество 1:1 в `destroy:mixture` с молекулами
  Destroy. Концентрации передаёт вызывающий рецепт; production-значение будет в JSON на M2, не зашито в мост.
- `AtomicFluidOperation`: сначала проверяет вход и свободное место во всех выходах через `SIMULATE`, затем одной
  основно-поточной операцией списывает вход и заполняет выходы. Полный любой выход запрещает всю партию.
- Devtest-only контроллер получает воду из отдельного конечного бака через NeoForge fluid capability и
  `FluidUtil.tryFluidTransfer`, питание — от настоящей creative battery EE через провода. Тестовые блоки не попадают
  в release jar.

## Зависимости

`./libs.sh` копирует точные jar сборки в `libs/`, извлекает необходимые jar-in-jar только для `compileOnly`, проверяет
SHA-256 кандидатов и записывает `libs/manifest.txt`. Чужие jar не пересобираются и не вкладываются в аддон.

| Зависимость | Версия         | SHA-256                                                            |
| ----------- | -------------- | ------------------------------------------------------------------ |
| Destroy     | 0.4.6-eligibbs | `8fb94ff4fc124671b3408ca13b9fe471a8a2dacfc79218fddda3308c42a02862` |
| Petrolpark  | 1.5.11         | `f9870f0c9c654d962d89a485c60d971181556c48bea345964ee5a07bc54095f4` |

Остальные версии и хеши фиксируются в `libs/manifest.txt`. Destroy и Petrolpark — All Rights Reserved; эта среда
используется для локальной разработки по решению владельца. Сайтовый выпуск и распространение зависимостей здесь не
выполняются.

## Сборка и стенд

```bash
export JAVA_HOME="$HOME/.gradle/local-tools/jdk21/Contents/Home"
./gradlew build --console=plain
./test-server.sh
```

`test-server.sh` поддерживает `PROFILE=minimal|thermal|sable`, `KEEP=1`, `OLD_JAR`, `RCON_PORT` и `SERVER_PORT`.
Сервер останавливается после сценария; `DESTROYTEST FAIL`, неизвестная команда и загрузочный сбой дают ненулевой код.
Логи — `run/check.log`, ответы — `run/results.txt`, состав стенда — `run/manifest.txt`.

Команды:

- `/destroytest environment` — Castner и серверная загрузка datapack-реакции;
- `/destroytest build` — источник, контроллер, батарея и провода;
- `/destroytest power <V>` — напряжение после инициализации EE-устройств;
- `/destroytest fill hydrogen|chlorine|sodium_hydroxide` — заполнить один выход;
- `/destroytest report` — состояние, баки, прогресс, измеренные V/A и компоненты смеси;
- `/destroytest restartcase` — сохранить контрольный прогресс и точный снимок смеси, отключить питание;
- `/destroytest assert normal|undervoltage|blocked_hydrogen|blocked_chlorine|blocked_sodium_hydroxide|persisted`;
- `/destroytest fail` — намеренно падающая проверка самого стенда.

Пример нормальной партии:

```bash
./test-server.sh \
  'destroytest build' 'sleep 1' 'destroytest power 300' \
  'sleep 11' 'destroytest report' 'destroytest assert normal'
```

Проверка перезапуска:

```bash
./test-server.sh \
  'destroytest build' 'sleep 1' 'destroytest power 300' \
  'sleep 5' 'destroytest restartcase'
KEEP=1 ./test-server.sh 'destroytest report' 'destroytest assert persisted'
```

## Фактические проверки

- Чистые сборки обоих аддонов проходят на JDK 21.
- `minimal`, `thermal` и `sable`: среда `PASS 2/2`; намеренно сломанная проверка даёт `FAIL` и ненулевой код.
- Норма: **299,827 В, 7,995 А**, `PASS 12/12`; одна партия 1000 мБ даёт 250 мБ H₂ + 250 мБ Cl₂ + 500 мБ NaOH.
  Проверяются не только fluid id, но и состав/data components; массовый остаток — 7000 мБ из исходных 8000 мБ.
- Просадка до 200 В: 5,330 А, `WAITING_FOR_POWER`, `PASS 7/7`; возможен один переходный тик EE, затем прогресс не
  растёт, новая партия и продукты не расходуются.
- Каждое из трёх переполнений: `PASS 5/5`; другие выходы пусты, вход не списан, частичного продукта нет.
- Полный перезапуск через `KEEP=1`: `PASS 6/6`; прогресс 121 тик, баки, точный NBT смеси и отсутствие повторной
  выдачи сохранены.

## Что ещё не проверено и не реализовано

- Нет Survival-электролизёра, мембраны, рецепта `destroy_extras:electrolysis/salt_water`, UI/JEI/EMI и игровых портов.
- Не выполнялись клиентская проверка, полный pack/site audit и миграция настоящего старого мира. `OLD_JAR` — только
  поддержка сценария, а не самостоятельно пройденная миграционная проверка.
- `thermal` — урезанный стенд: базовые ошибки Wooden Cog о предметах TFC и diving items Create не относятся к новым
  аддонам; это не полный аудит совместимости сборки.
- У Petrolpark 1.5.11 необязательная JEI-интеграция рассчитана на более новый JEI, чем 19.44.0.401 в сборке;
  клиентская совместимость ещё не проверена.
- Загрузка сайтовых датапаков заблокирована локальной ошибкой `firebase-tools/lib/auth`; полный сайтовый аудит не
  выполнен.
