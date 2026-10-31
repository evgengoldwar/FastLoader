# FastLoader

Мод для Minecraft 1.7.10 (GTNH), который подробно логирует **весь путь чанка**: загрузку с диска, генерацию
рельефа, популяцию (включая каждый `IWorldGenerator` каждого мода и GregTech-руды), освещение, события Forge,
сохранение и отправку игрокам. Задача — найти, что именно съедает MSPT при генерации мира.

Это первый этап (диагностика). Оптимизации будут делаться по результатам отчётов.

## Что измеряется

| Этап | Где перехватывается |
|---|---|
| Запрос чанка (sync / async / очередь) | `ChunkProviderServer.loadChunk(x, z, Runnable)` (Forge) |
| Загрузка/генерация | `ChunkProviderServer.originalLoadChunk`, `safeLoadChunk` |
| Рельеф | `IChunkProvider.provideChunk` (по классу провайдера: Overworld, Nether, Twilight, Galacticraft, ...) |
| Шум рельефа, биомы | `ChunkProviderGenerate.func_147424_a`, `replaceBlocksForBiome`, `WorldChunkManager` (GenLayer) |
| Пещеры, овраги, структуры | `MapGenBase.func_151539_a`, `MapGenStructure.generateStructuresInChunk` (по классу) |
| Популяция | `ChunkProviderServer.populate` → `IChunkProvider.populate` → `GameRegistry.generateWorld` |
| Генераторы модов | каждый `IWorldGenerator` отдельно, с id мода |
| Декорации биомов | `BiomeDecorator.decorateChunk` (по биому), `generateOres`, `WorldGenMinable`, озёра, данжи |
| Спавн мобов при генерации | `SpawnerAnimals.performWorldGenSpawning` |
| Слушатели событий | каждый `@SubscribeEvent`, вызванный внутри работы с чанками (`ChunkEvent.Load`, `PopulateChunkEvent`, `DecorateBiomeEvent`, `OreGenEvent`, GT `VeinGenerateEvent`...), с id мода |
| Освещение | `Chunk.func_150809_p` (первичный свет), `generateSkylightMap`, `func_150804_b`; `World.updateLightByType` — считается каждый вызов, замеряется 1 из 32 (оценка стоимости без искажения) |
| Запись блоков | `World.setBlock` — счётчик по секциям (какой генератор ставит сколько блоков), 1 из 64 вызовов замеряется вместе с внутренностями: `Chunk.func_150807_a`, `relightBlock`, проверка света, `markAndNotifyBlock`; плюс счётчик запланированных тиков блоков |
| **RWG** | `generateTerrain` (шум высот), `generateMapGen` по биомам, `replaceBlocksForBiome`, `rDecorate` (декораторы биомов), `WorldGenLiquids` |
| Диск / NBT | `AnvilChunkLoader.loadChunk__Async`, `readChunkFromNBT`, `loadEntities`, `saveChunk`, `writeNextIO` (поток File IO), ChunkIO stage2 |
| Выгрузка/сохранение | `unloadQueuedChunks`, `safeSaveChunk`, `saveChunks`, `Chunk.onChunkUnload` |
| Игроки | `PlayerManager.updatePlayerInstances`, `updatePlayerPertinentChunks`, сборка пакетов `S21PacketChunkData` |
| **GregTech** | `WorldGenContainer.run`, `generateVein`, каждая рудная жила (`ore.mix.*`), камни, мелкие руды, очередь отложенных задач GT |

Плюс:

* **Выборка стеков** — раз в 10 мс снимается стек серверного потока, пока он занят чанками. В отчёте по каждой секции
  видно, какой метод мода и какой код на вершине стека реально тратит время, даже там, куда нет отдельных хуков
  (декораторы RWG, генераторы модов, внутренности `setBlock`).

* **Каскадная генерация** — когда во время популяции одного чанка генерируется/грузится соседний. Это главный
  убийца TPS в модпаках. Для каждого случая пишется виновник (конкретный `IWorldGenerator`, жила GT, слушатель
  события...) и путь вызовов.
* **Медленные чанки** — чанк, генерация/популяция которого заняла больше порога, логируется с разбивкой по
  self-time.
* **Медленные тики** — тик дольше порога логируется с разбивкой: сколько из него ушло на чанки и на что именно,
  плюс время тика каждого мира.
* **Периодический отчёт** (по умолчанию раз в 60 с): MSPT, чанки/с, гистограммы времени рельефа/популяции,
  каскады по виновникам, топ секций по self-time, топ генераторов модов, топ слушателей событий, GT,
  и полное дерево вызовов отдельно для каждого потока (Server thread, Chunk I/O Executor, File IO Thread).

## Оптимизации

Категория `optimizations` в `config/fastloader.cfg`. Ни одна из них не меняет то, что генерируется.

| Опция | Что делает |
|---|---|
| `chunkLookupCache` | Кэш на 4 последних чанка перед `ChunkProviderServer.provideChunk`. Каждый `getBlock`/`setBlock`/запрос света ищет чанк в хэш-таблице; при генерации это десятки тысяч поисков на чанк, почти всегда по одним и тем же соседним чанкам. Строка `Chunk lookup cache: ... % hits` в отчёте показывает, насколько часто кэш срабатывает. |

## Использование

Никаких команд не нужно:

1. Положить мод в `mods/` (нужен GTNHMixins/UniMixins, как у всех модов GTNH).
2. Зайти в мир и просто летать / бегать / телепортироваться, чтобы генерировались новые чанки.
3. Как только ты перестаёшь генерировать (по умолчанию 10 с без новых чанков), отчёт пишется автоматически.
   При долгой непрерывной генерации — ещё и раз в 60 с. Время, когда чанки не генерировались, в отчёты не попадает.
4. Смотреть:
   * `fastloader/chunkprofile-<дата>.log` в папке игры — полные отчёты с деревом вызовов (новый файл на каждый
     запуск мира);
   * основной лог (`logs/fml-client-latest.log` в одиночке, `logs/fml-server-latest.log` на сервере) — строки
     `[slow chunk]`, `[slow tick]`, `[cascade]` и краткие сводки `Chunk profile (...)`.

Команды ниже — необязательные (ручной отчёт, бенчмарк-прегенерация).

### Команды (`/fastloader` или `/fl`, уровень OP 2)

| Команда | Что делает |
|---|---|
| `/fl profile status` | краткая сводка текущего интервала в чат |
| `/fl profile report` | полный отчёт прямо сейчас (лог + файл) |
| `/fl profile reset` | начать новый интервал |
| `/fl profile on` / `off` | включить / выключить профайлер |
| `/fl pregen <радиус> [dim] [chunkX chunkZ]` | прегенерация квадратной спиралью (бенчмарк) |
| `/fl pregen status` / `stop` | статус / остановка |

### Конфиг (`config/fastloader.cfg`)

`profiler`: `enabled`, `reportAfterIdleSeconds`, `reportIntervalSeconds`, `slowChunkThresholdMs`, `slowTickThresholdMs`,
`maxLogsPerInterval`, `logCascades`, `profileLighting`, `countSetBlock`, `profileEventListeners`, `topEntries`,
`writeReportFiles`, `callTreeInLog`, `stackSampling`, `stackSampleIntervalMs`.
`pregen`: `tickBudgetMs` (сколько мс тика можно тратить на генерацию), `progressLogSeconds`.

## Как читать отчёт

* **self ms** — время внутри самой секции без вложенных. Сортировка по нему показывает, *где реально* тратится
  время. **total ms** — включая вложенные.
* `provideChunk (terrain): X` — генерация рельефа провайдером X. `populate: X` — популяция провайдером X
  (без генераторов модов). `IWorldGenerator: ...` — генератор мода. `Event ... -> класс.метод [мод]` — слушатель.
* `Cascading worldgen ... <- виновник` — сколько чанков было сгенерировано/загружено «в цепочке» и кем.
* `Chunk work on server thread` — сколько миллисекунд тика в среднем уходит на чанки.
* `GregTech pending oregen tasks` — сколько отложенных задач рудогенерации GT ждёт выполнения (GT выполняет
  максимум 5 за вызов).

Пришлите файл `fastloader/chunkprofile-*.log` и кусок лога со строками `[slow tick]`/`[cascade]` после
прегенерации — по ним будет понятно, что оптимизировать в первую очередь.

## Накладные расходы

Каждая секция — два вызова `System.nanoTime()` и запись в дерево текущего потока без синхронизации.
Освещение и слушатели событий измеряются только внутри работы с чанками. Выключается полностью командой
`/fl profile off` или `enabled=false` в конфиге.

## Сборка

```bash
./gradlew build
```

Требуется доступ к `nexus.gtnewhorizons.com` (RetroFuturaGradle, GTNHMixins).
