# Destroy Extras

Недостающие химические машины Destroy для dev-задачи #22. **Версия 0.1.0 остаётся разработческой и не выпускается
игрокам.** Реализованы мембранный электролизёр M2, цепочка HCl/FeCl₃ M3 и травильная ванна L1 M4.

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

JEI-категория находится в отдельном `compat/jei`; JEI объявлен необязательной клиентской зависимостью. Компиляция и
ресурсы проверены, клиентский запуск — нет.

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
- Север — выход отработанного раствора, остальные стороны — вход; item handler открывает два входа и готовую плату.
  Смена входов сбрасывает прогресс. Баки, предметы, рецепт и прогресс сохраняются в NBT и в `BlockEntityData` выпавшей
  ванны; повторная установка восстанавливает их штатным путём.

Проверки на dedicated server: окружение `PASS 6/6`; циклы devtest и настоящей L1 — по `PASS 10/10`, 250 мБ раствора,
3 Cu(II), 6 Fe(II), точная топология; переполненный выход — `PASS 3/3`, отсутствие Fe(III) — `PASS 2/2`; разрушение и
повторная установка — `PASS 6/6`; перезапуск на 100 тиках — `PASS 5/5`, затем завершение L1 — `PASS 10/10`. Регрессия
M2 и всех сценариев M3 проходит. Ресурсы и модели проверены; клиент/JEI не запускался.

```bash
./test-server.sh 'destroyextratest m4build' 'destroyextratest m4acid' \
  'sleep 2' 'destroyextratest m4load l1' 'sleep 21' 'destroyextratest m4assert l1'
```

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

Не выполнялись клиентский запуск/JEI, полный pack/site audit и выпуск игрокам. У Petrolpark 1.5.11 известна
несовместимость необязательной JEI-интеграции с версией JEI сборки; это отдельный клиентский риск, не закрытый M2.
