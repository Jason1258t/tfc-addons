// Тест схематичной пушки: A — творческий ящик, B — бочки с точным набором + повторный запуск, C — сброс посреди печати
const CT_SBE_State = Java.loadClass('com.simibubi.create.content.schematics.cannon.SchematicannonBlockEntity$State')
const CT_AllBlocks = Java.loadClass('com.simibubi.create.AllBlocks')
const CT_AllItems = Java.loadClass('com.simibubi.create.AllItems')
const CT_ADC = Java.loadClass('com.simibubi.create.AllDataComponents')
const CT_Printer = Java.loadClass('com.simibubi.create.content.schematics.SchematicPrinter')
const CT_AllConfigs = Java.loadClass('com.simibubi.create.infrastructure.config.AllConfigs')
const CT_BlockPos = Java.loadClass('net.minecraft.core.BlockPos')
const CT_Heightmap = Java.loadClass('net.minecraft.world.level.levelgen.Heightmap$Types')
const CT_ItemStack = Java.loadClass('net.minecraft.world.item.ItemStack')
const CT_Items = Java.loadClass('net.minecraft.world.item.Items')
const CT_Blocks = Java.loadClass('net.minecraft.world.level.block.Blocks')
const CT_Rotation = Java.loadClass('net.minecraft.world.level.block.Rotation')
const CT_Mirror = Java.loadClass('net.minecraft.world.level.block.Mirror')
const CT_Direction = Java.loadClass('net.minecraft.core.Direction')
const CT_Dirs = ['north', 'south', 'east', 'west', 'down']

let ct = null

function ctLog(msg) {
  console.info('[CT] ' + msg)
}

function ctBlueprint(anchor, file) {
  var s = CT_AllItems.SCHEMATIC.asStack()
  s.set(CT_ADC.SCHEMATIC_FILE, file)
  s.set(CT_ADC.SCHEMATIC_OWNER, 'Test')
  s.set(CT_ADC.SCHEMATIC_ANCHOR, anchor)
  s.set(CT_ADC.SCHEMATIC_DEPLOYED, true)
  s.set(CT_ADC.SCHEMATIC_ROTATION, CT_Rotation.NONE)
  s.set(CT_ADC.SCHEMATIC_MIRROR, CT_Mirror.NONE)
  return s
}

function ctId(state) {
  return String(state.getBlock()).replace(/^Block\{(.*)\}$/, '$1')
}

// Что ещё отличается от схемы: { total, groups }
function ctCompare(level, anchor, file) {
  var p = new CT_Printer()
  p.loadSchematic(ctBlueprint(anchor, file), level, true)
  var groups = {}
  var total = 0
  var guard = 0
  while (p.advanceCurrentPos() && guard++ < 300000) {
    p.shouldPlaceCurrent(level, (pos, state, be, toReplace, other, normal) => {
      if (state.isAir()) return false
      total++
      var key
      if (toReplace == state) {
        var wbe = level.getBlockEntity(pos)
        var a = be ? String(be.saveWithoutMetadata(level.registryAccess())) : ''
        var b = wbe ? String(wbe.saveWithoutMetadata(level.registryAccess())) : ''
        total--
        if (a == b) return false
        total++
        key = 'данные: ' + ctId(state)
        if (!groups[key]) groups[key] = { n: 0, ex: 'схема ' + a.substring(0, 300) + ' | мир ' + b.substring(0, 300) + ' @ ' + pos.toShortString() }
        groups[key].n++
        return false
      }
      if (toReplace.isAir()) key = 'нет: ' + ctId(state)
      else if (toReplace.getBlock() == state.getBlock()) key = 'свойства: ' + ctId(state)
      else key = 'другой: ' + ctId(state) + ' ← ' + ctId(toReplace)
      if (!groups[key]) groups[key] = { n: 0, ex: String(state) + ' | мир: ' + String(toReplace) + ' @ ' + pos.toShortString() }
      groups[key].n++
      return false
    })
  }
  return { total: total, groups: groups }
}

// Сколько предметов нужно (все непустые клетки схемы, мир пустой)
function ctRequired(level, anchor, file) {
  var p = new CT_Printer()
  p.loadSchematic(ctBlueprint(anchor, file), level, true)
  var req = {}
  var guard = 0
  var rq
  while (p.advanceCurrentPos() && guard++ < 300000) {
    if (!p.shouldPlaceCurrent(level)) continue
    rq = p.getCurrentRequirement()
    if (rq.isInvalid()) {
      req['INVALID'] = (req['INVALID'] || { n: 0 })
      req['INVALID'].n++
      continue
    }
    rq.getRequiredItems().forEach((sr) => {
      var id = String(sr.stack.getItem())
      if (!req[id]) req[id] = { n: 0, stack: sr.stack.copyWithCount(1) }
      req[id].n += sr.stack.getCount()
    })
  }
  return req
}

function ctCount(level, cannonPos) {
  var c = {}
  CT_Dirs.forEach((d) => {
    var be = level.getBlockEntity(cannonPos.relative(CT_Direction.byName(d)))
    if (!be || !be.getContainerSize) return
    for (let i = 0; i < be.getContainerSize(); i++) {
      var s = be.getItem(i)
      if (s.isEmpty()) continue
      var id = String(s.getItem())
      c[id] = (c[id] || 0) + s.getCount()
    }
  })
  return c
}

// Бочки вокруг пушки пополняются из запаса (стаки TFC маленькие, всё сразу не влезает)
function ctRefill(level, site) {
  var ids = Object.keys(site.bank).filter((id) => site.bank[id].left > 0)
  var k = 0
  CT_Dirs.forEach((d) => {
    var be = level.getBlockEntity(site.cannon.relative(CT_Direction.byName(d)))
    if (!be || !be.getContainerSize) return
    for (var i = 0; i < be.getContainerSize(); i++) {
      if (!be.getItem(i).isEmpty()) continue
      while (k < ids.length && site.bank[ids[k]].left <= 0) k++
      if (k >= ids.length) return
      var bk = site.bank[ids[k]]
      var n = Math.min(bk.left, bk.stack.getMaxStackSize())
      be.setItem(i, bk.stack.copyWithCount(n))
      bk.left -= n
      site.given[ids[k]] += n
      k = (k + 1) % ids.length
      ids = ids.filter((id) => site.bank[id].left > 0)
      if (!ids.length) return
      k = k % ids.length
    }
  })
}

function ctSite(level, name, x, z, creative, file) {
  level.getChunk(x >> 4, z >> 4)
  level.getChunk((x - 6) >> 4, (z - 6) >> 4)
  var y = level.getHeight(CT_Heightmap.WORLD_SURFACE, x, z)
  var anchor = new CT_BlockPos(x, y, z)
  var cannonPos = new CT_BlockPos(x - 6, y, z - 6)
  var site = { name: name, anchor: anchor, cannon: cannonPos, creative: creative, phase: 'run1', given: {}, file: file }
  if (!creative) {
    var req = ctRequired(level, anchor, file)
    site.req = req
  }
  level.setBlock(cannonPos, CT_AllBlocks.SCHEMATICANNON.getDefaultState(), 3)
  if (creative) {
    level.setBlock(cannonPos.below(), CT_AllBlocks.CREATIVE_CRATE.getDefaultState(), 3)
  } else {
    CT_Dirs.forEach((d) => level.setBlock(cannonPos.relative(CT_Direction.byName(d)), CT_Blocks.BARREL.defaultBlockState(), 3))
    site.bank = {}
    Object.keys(site.req).forEach((id) => {
      var r = site.req[id]
      if (!r.stack) return
      site.given[id] = 0
      site.bank[id] = { left: Math.ceil(r.n * 1.1) + 16, stack: r.stack }
    })
    ctRefill(level, site)
    ctLog(name + ': нужно ' + JSON.stringify(Object.keys(site.req).map((k) => k + '=' + site.req[k].n)) + ' нет предмета ' + (site.req['INVALID'] ? site.req['INVALID'].n : 0))
  }
  var be = level.getBlockEntity(cannonPos)
  be.inventory.setStackInSlot(0, ctBlueprint(anchor, file))
  if (!creative) be.inventory.setStackInSlot(4, Item.of('minecraft:gunpowder', 64))
  be.replaceMode = 2
  be.skipMissing = false
  be.state = CT_SBE_State.RUNNING
  be.setChanged()
  ctLog(name + ': якорь ' + anchor.toShortString() + ', пушка ' + cannonPos.toShortString())
  return site
}

function ctReport(level, site, label) {
  var cmp = ctCompare(level, site.anchor, site.file)
  ctLog(site.name + ' ' + label + ': не совпадает ' + cmp.total)
  Object.keys(cmp.groups)
    .sort((a, b) => cmp.groups[b].n - cmp.groups[a].n)
    .forEach((k) => ctLog('   ' + cmp.groups[k].n + ' × ' + k + '   пример: ' + cmp.groups[k].ex))
  if (!site.creative) {
    var now = ctCount(level, site.cannon)
    var used = {}
    var sum = 0
    var spent = {}
    Object.keys(site.given).forEach((id) => {
      spent[id] = site.given[id] - (now[id] || 0)
      var u = spent[id] - (site.spent ? site.spent[id] || 0 : 0)
      if (u) used[id] = u
      sum += u
    })
    ctLog(site.name + ' ' + label + ': потрачено всего ' + sum + ' ' + JSON.stringify(used))
    site.spent = spent
  }
  return cmp
}

ServerEvents.commandRegistry((event) => {
  var { commands: Commands } = event
  event.register(
    Commands.literal('ct')
      .then(
        Commands.literal('start').executes((ctx) => {
          var server = ctx.source.server
          var level = server.overworld()
          try {
          CT_AllConfigs.server().schematics.schematicannonDelay.set(4)
          ct = { tick: 0, sites: [] }
          server.runCommandSilent('forceload add 160 150 300 280')
          server.runCommandSilent('forceload add 460 150 600 280')
          ct.sites.push(ctSite(level, 'R', 200, 200, false, 's4.nbt'))
          var o = ctSite(level, 'O', 500, 200, false, 's1.nbt')
          var y = o.anchor.getY()
          server.runCommandSilent('fill 500 ' + y + ' 200 517 ' + (y + 3) + ' 217 tfc:ore/amethyst/andesite')
          ct.sites.push(o)
          ctLog('старт')
          } catch (e) {
            ctLog('ошибка старта: ' + e + ' ' + (e.stack || ''))
            if (e.javaException) e.javaException.printStackTrace()
          }
          return 1
        })
      )
      .then(
        Commands.literal('status').executes((ctx) => {
          var level = ctx.source.server.overworld()
          if (!ct) return 0
          ct.sites.forEach((s) => {
            var be = level.getBlockEntity(s.cannon)
            ctLog(s.name + ' ' + s.phase + ': ' + be.state + ' ' + be.statusMsg + ' ' + be.blocksPlaced + '/' + be.blocksToPlace)
          })
          return 1
        })
      )
  )
})

ServerEvents.tick((event) => {
  if (!ct || ct.failed) return
  ct.tick++
  if (ct.tick % 20 != 0) return
  try {
    ctTick(event)
  } catch (e) {
    ct.failed = true
    ctLog('ошибка тика: ' + e)
  }
})

function ctTick(event) {
  var level = event.server.overworld()
  ct.sites.forEach((s) => {
    var be = level.getBlockEntity(s.cannon)
    if (!be) return
    if (!s.creative) ctRefill(level, s)
    // кнопка «Пуск» после загрузки схемы
    if (be.state == CT_SBE_State.PAUSED && String(be.statusMsg) == 'ready' && s.phase != 'reset') {
      be.state = CT_SBE_State.RUNNING
      return
    }
    // C: сброс и повторный пуск посреди печати, как кнопки «Стоп» и «Пуск»
    if (s.name == 'C' && s.phase == 'run1' && be.blocksPlaced > 1500) {
      ctLog('C: сброс на ' + be.blocksPlaced)
      be.state = CT_SBE_State.STOPPED
      s.phase = 'reset'
      return
    }
    if (s.phase == 'reset') {
      be.state = CT_SBE_State.RUNNING
      s.phase = 'run2'
      return
    }
    var done = be.state == CT_SBE_State.STOPPED && String(be.statusMsg) == 'finished'
    if (!done) return
    if (s.phase == 'run1' && !s.creative) {
      ctReport(level, s, 'после печати')
      // повторный запуск той же схемы поверх готовой постройки
      be.inventory.setStackInSlot(1, CT_ItemStack.EMPTY)
      be.inventory.setStackInSlot(0, ctBlueprint(s.anchor, s.file))
      be.state = CT_SBE_State.RUNNING
      s.phase = 'again'
      return
    }
    if (s.phase == 'run1' || s.phase == 'again' || s.phase == 'run2') {
      ctReport(level, s, s.phase == 'again' ? 'после повторного запуска' : 'после печати')
      if (s.name == 'C' && s.phase == 'run2') {
        be.inventory.setStackInSlot(1, CT_ItemStack.EMPTY)
        be.inventory.setStackInSlot(0, ctBlueprint(s.anchor, s.file))
        be.state = CT_SBE_State.RUNNING
        s.phase = 'again'
        return
      }
      s.phase = 'done'
      ctLog(s.name + ': готово, тиков ' + ct.tick)
    }
  })
}
