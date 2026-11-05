# Исходники текстур поверхностной проводки

Иконки сгенерированы в ImageGen с визуальным референсом `ee_extras:item/power_cable_spool`. Общая часть запроса:

> Minecraft Create inventory sprite. [Описание предмета.] One isolated item fills canvas with small margin. Very simple readable logical 16x16 pixel art, flat limited palette, no gradients, transparent background, no text. Reference is for pack sprite detail and palette only.

| Предмет | Описание в запросе |
| --- | --- |
| Катушка поверхностного провода | Small diagonal dark cable spool, brown flanges, flat twin charcoal cable, two adjacent exposed copper dots at cut end |
| Катушка тонкого провода | Small diagonal light gray cable spool, brown flanges, thin tightly wound insulated cable, one copper end |
| Крышка | Small charcoal rectangular bakelite electrical cover in simple pixel isometric view, two brass screw terminals and a black twin cable entry |

Игровые PNG сведены к 16×16 с непрозрачным силуэтом и прозрачным полем. Текстуры граней используют всю область UV. У полос провода координата U идёт вдоль провода, V — по сечению.

Финальная иконка коробки сгенерирована по фотографии пользователя. Запрос:

> Create one isolated Minecraft Create inventory sprite based on the attached real junction box. Match its square clean flat pale gray plastic lid with slight overhanging lip, short straight body and circular knockouts only on visible side faces, two side-by-side on the wide side and one on the other. No hanging wire, no external tabs. Four discreet recessed screw pixels on lid. Use very simple logical 16x16 pixel shapes, neutral gray slightly dark sides, limited flat palette, strong contrast, no gradient or reflections. Isometric view, sprite fills canvas with small margin, true transparent background, no text.
