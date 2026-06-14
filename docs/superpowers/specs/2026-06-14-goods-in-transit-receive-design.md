# Приход товара в пути + распределение по складу (Android ТСД-клиент)

**Дата:** 2026-06-14
**Статус:** утверждён дизайн
**Репозитории:** `inventory` (Android, основная работа) + `cloudmarket-server` (Go, мини-правка)

## Цель

Реализовать в Android-приложении `io.proffi.inventory` (ТСД-сканер) приёмку
товара «в пути» (goods-in-transit) от поставщика с одновременным распределением
по ячейкам склада. Backend модуль `documents/goods_in_transit_*` в
`cloudmarket-server` уже написан и используется как готовый API (+ мелкое
обогащение ответов именами товаров).

## Контекст

### Backend (готов)
Модуль `documents/goods_in_transit_*.go`. Статусы:
`in_transit → receiving` (авто на первом скане) `→ completed` / `cancelled`.

Эндпоинты под `/api/v1/documents/`:
- `GET  /goods-in-transit/?status=&warehouse_id=&page=` — список (фильтр статуса = точное совпадение, один статус)
- `GET  /goods-in-transit/:id/` — деталь (header + details + scans)
- `POST /goods-in-transit/:id/scan/` — `{barcode, box_code, quantity}` (все required)
- `POST /goods-in-transit/:id/finalize/` — приход на сток + раскладка по ячейкам + акт расхождений
- `POST /goods-in-transit/:id/cancel/`
- `POST /excel/goods-in-transit/create/` — создание дока из Excel (web/менеджер, **не** Android)

`finalize` атомарно: создаёт receipt → проводит сток + долг поставщику + аналитику →
раскладывает сканы по ячейкам (`box_code` каждого скана) → пишет акт расхождений
(где принято ≠ заказано) → ставит `completed`.

**«Распределение по складу»** = обязательный `box_code` на каждом скане. Раскладка
неотделима от приёма — нельзя принять товар не указав ячейку.

### Android (клиент)
ТСД-приложение. Существующие схожие потоки: `productreceive` (приём
внутреннего перемещения), `productmove`, `packing`, `assembly`, `warehouse`.
Паттерн: Retrofit `ApiService` → `*Repository` (Result-обёртки) →
`*ViewModel` (StateFlow + sealed states) → Compose `Activity`. DI через Koin
(`di/AppModule.kt`). Меню — drawer в `ui/main/MainActivity.kt`.

Образец фазового скан-экрана «ячейка → товар в ячейку»:
`ui/packing/PackingScannerActivity.kt` (фазы `WaitingForBox → BoxScanned`).

## Выбранный подход

**Вариант A — по образцу Packing-экрана.** Полный ТСД-UX с фазовой моделью
скана и списком план/факт (заказано vs принято).

Отклонённые:
- **B (lean «слепой приём»)** — без сравнения план/факт; хуже UX, оператор не видит остаток.
- **C (двухфазный приём→раскладка)** — противоречит контракту: `scan` требует `box_code`.

## Поток

```
MainActivity drawer → "Приход в пути"
  → GoodsInTransitSelectionActivity
        выбор склада (как ProductReceiveSelectionActivity)
        → GET список GIT-доков, client-фильтр {in_transit, receiving}
  → тап по доку → GoodsInTransitScannerActivity
        load detail (план: заказано/принято, имена товаров)
        фаза WaitingForCell:  скан ячейки → box_code
        фаза CellScanned:     скан товаров → POST scan {barcode, box_code, qty}
                              эхо имени товара, прогресс план/факт растёт
        кнопка "Завершить приёмку" → POST finalize → назад в список
        (опц.) "Отменить" → POST cancel
```

## Компоненты (Android)

| Файл | Роль |
|---|---|
| `network/ApiService.kt` (правка) | +5 GIT-эндпоинтов + DTO |
| `data/GoodsInTransitRepository.kt` (новый) | Result-обёртки (как `ProductMoveRepository`) |
| `ui/goodsintransit/GoodsInTransitViewModel.kt` (новый) | StateFlow: список, detail, фаза, scan, finalize, cancel |
| `ui/goodsintransit/GoodsInTransitSelectionActivity.kt` (новый) | выбор склада → список доков |
| `ui/goodsintransit/GoodsInTransitScannerActivity.kt` (новый) | фазовый скан-экран (по образцу `PackingScannerActivity`) |
| `di/AppModule.kt` (правка) | регистрация репо + viewModel |
| `ui/main/MainActivity.kt` (правка) | пункт меню drawer + колбэк |
| `res/values/strings.xml`, `res/values-en/strings.xml` (правка) | строки RU/EN |

### Скан-логика (как packing)
- `onScanResult(barcode)`: в фазе `WaitingForCell` → `onCellSet(barcode)`; в фазе
  `CellScanned` → `onProductScanned(barcode)` → POST scan.
- Камера: ручной ввод кол-ва + ручной ввод ячейки. Аппаратный сканер (UROVO): qty=1.
- После успешного скана — обновление прогресса (локально или reload detail).
- Кнопка «Сменить ячейку» сбрасывает фазу в `WaitingForCell`.

## Backend мини-правка (cloudmarket-server)

Без новых таблиц/миграций.

| Место | Изменение |
|---|---|
| `documents/goods_in_transit_serializers.go` `GITDetailLine` | +`product_name`, `barcode`, `article` |
| `documents/goods_in_transit_repo.go` `ListGITDetails` / `GITDetail` | JOIN `products p ON p.id = d.product_id`, заполнить новые поля |
| `GITScanLine` + `ListGITScans` | +`product_name` (JOIN products; scans уже джойнят details) |
| `documents/goods_in_transit_controller.go` `GoodsInTransitScan` | вернуть body `{barcode, product_name, quantity}` (как product-move `ScanProductMoveBody`) вместо пустого `response.Correct()` |
| `documents/goods_in_transit_service.go` `ScanGoodsInTransit` | вернуть резолвнутое имя товара + qty наверх в контроллер |

## API-контракт (для Android)

- `GET /api/v1/documents/goods-in-transit/?warehouse_id=&page=` → пагинированный
  список `GITListItem` (`id`, `document_number`, `external_document_number`,
  `warehouse_id`, `status`, `line_count`, `ordered_total`, `received_total`).
  **Статус не передаём** — клиент фильтрует `{in_transit, receiving}` (backend
  фильтр поддерживает один статус, а нужны два).
- `GET /api/v1/documents/goods-in-transit/:id/` → `GITDetailView`
  (`details[]` с `product_name`/`barcode`/`article` + ordered/received,
  `scans[]` с `product_name`/`warehouse_box_id`/`quantity`).
- `POST /api/v1/documents/goods-in-transit/:id/scan/`
  body `{barcode, box_code, quantity}` → `{barcode, product_name, quantity}`.
- `POST /api/v1/documents/goods-in-transit/:id/finalize/` → `Correct()`.
- `POST /api/v1/documents/goods-in-transit/:id/cancel/` → `Correct()`.

## Краевые случаи / ошибки

| Случай | Поведение |
|---|---|
| Скан товара не из дока | 400 → snackbar «товар не в документе» |
| Скан товара до выбора ячейки | заблокировано фазой `WaitingForCell` |
| Ячейка не в этом складе | 400 → snackbar |
| Finalize не из статуса `receiving` | 409 → snackbar |
| Неизвестный штрихкод | 400 → snackbar |
| qty: камера / аппаратный сканер | ручной ввод / 1 (как в существующих экранах) |
| Расхождение план/факт | UI не блокирует; сервер сам пишет акт расхождений на finalize |

## Scope / YAGNI

**Входит:** список доков, приём+раскладка по ячейкам (scan), finalize, cancel,
пункт меню, строки RU/EN, мини-правка backend (имена товаров).

**Не входит:** создание GIT-дока (Excel на web/менеджере), редактирование
цен/количеств заказа, экран просмотра акта расхождений, оффлайн-режим,
печать этикеток.

## Тесты

- **Backend:** расширить `documents/goods_in_transit_integration_test.go` —
  проверить, что detail отдаёт `product_name`, scan-ответ содержит `product_name`.
- **Android:** проект не содержит unit-тестов на ViewModel — проверка ручная
  сборкой и прогоном на устройстве (как остальные экраны).
