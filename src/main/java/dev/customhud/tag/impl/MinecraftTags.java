package dev.customhud.tag.impl;

import dev.customhud.tag.core.TagCore;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Supplier;

/** Minecraft game state. Every tag returns a safe default outside a world. */
public final class MinecraftTags {
	private MinecraftTags() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	private static LocalPlayer player() {
		return mc().player;
	}

	private static <T> TagCore p(String name, Class<T> type, T fallback, String desc, Function<LocalPlayer, ?> getter) {
		return Def.value(name, type, desc, () -> {
			LocalPlayer pl = player();
			return pl == null ? fallback : getter.apply(pl);
		});
	}

	private static TagCore v(String name, Class<?> type, String desc, Supplier<?> getter) {
		return Def.value(name, type, desc, getter);
	}

	public static List<TagCore> all() {
		List<TagCore> t = new ArrayList<>();
		// performance / connection
		t.add(v("MemUsed", Double.class, "Used Java memory in MB",
				() -> (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1048576.0));
		t.add(v("MemMax", Double.class, "Maximum Java memory in MB", () -> Runtime.getRuntime().maxMemory() / 1048576.0));
		t.add(v("MemPercent", Double.class, "Used memory in percent", () -> {
			Runtime rt = Runtime.getRuntime();
			return 100.0 * (rt.totalMemory() - rt.freeMemory()) / rt.maxMemory();
		}));
		t.add(v("Ping", Integer.class, "Your latency to the server in ms", () -> {
			if (mc().getConnection() == null || player() == null) {
				return 0;
			}
			PlayerInfo info = mc().getConnection().getPlayerInfo(player().getUUID());
			return info == null ? 0 : info.getLatency();
		}));

		// player
		t.add(p("X", Double.class, 0d, "Player X coordinate", LocalPlayer::getX));
		t.add(p("Y", Double.class, 0d, "Player Y coordinate", LocalPlayer::getY));
		t.add(p("Z", Double.class, 0d, "Player Z coordinate", LocalPlayer::getZ));
		t.add(p("BlockX", Integer.class, 0, "X of the block you stand in", pl -> pl.blockPosition().getX()));
		t.add(p("BlockY", Integer.class, 0, "Y of the block you stand in", pl -> pl.blockPosition().getY()));
		t.add(p("BlockZ", Integer.class, 0, "Z of the block you stand in", pl -> pl.blockPosition().getZ()));
		t.add(p("OtherX", Double.class, 0d, "X in the other dimension (Nether ↔ Overworld)", pl -> pl.getX() * otherFactor(pl)));
		t.add(p("OtherZ", Double.class, 0d, "Z in the other dimension (Nether ↔ Overworld)", pl -> pl.getZ() * otherFactor(pl)));
		t.add(p("Yaw", Float.class, 0f, "Horizontal look angle (-180 to 180)", pl -> Mth.wrapDegrees(pl.getYRot())));
		t.add(p("Pitch", Float.class, 0f, "Vertical look angle (-90 up to 90 down)", LocalPlayer::getXRot));
		t.add(p("Facing", String.class, "", "Facing direction name",
				pl -> Component.translatable("direction.customhud." + pl.getDirection().getName()).getString()));
		t.add(p("FacingShort", String.class, "", "Facing direction as one letter (N/S/E/W)",
				pl -> pl.getDirection().getName().substring(0, 1).toUpperCase(Locale.ROOT)));
		t.add(p("Axis", String.class, "", "Facing axis such as +Z or -X", pl -> axis(pl.getDirection())));
		t.add(p("Health", Float.class, 0f, "Current health (20 = 10 hearts)", LocalPlayer::getHealth));
		t.add(p("MaxHealth", Float.class, 20f, "Maximum health", LocalPlayer::getMaxHealth));
		t.add(p("Absorption", Float.class, 0f, "Absorption (golden hearts)", LocalPlayer::getAbsorptionAmount));
		t.add(p("Food", Integer.class, 20, "Hunger level (0-20)", pl -> pl.getFoodData().getFoodLevel()));
		t.add(p("Saturation", Float.class, 0f, "Saturation level", pl -> pl.getFoodData().getSaturationLevel()));
		t.add(p("ArmorPoints", Integer.class, 0, "Armor points (0-20)", LocalPlayer::getArmorValue));
		t.add(p("Air", Integer.class, 300, "Remaining air under water", LocalPlayer::getAirSupply));
		t.add(p("MaxAir", Integer.class, 300, "Maximum air", LocalPlayer::getMaxAirSupply));
		t.add(p("XpLevel", Integer.class, 0, "Experience level", pl -> pl.experienceLevel));
		t.add(p("XpProgress", Float.class, 0f, "Progress to the next level (0-1)", pl -> pl.experienceProgress));
		t.add(p("Speed", Double.class, 0d, "Horizontal speed in blocks per second", pl -> Trackers.speed(mc(), 0)));
		t.add(p("Speed3D", Double.class, 0d, "Speed including vertical movement", pl -> Trackers.speed(mc(), 1)));
		t.add(p("FallDistance", Double.class, 0d, "Current fall distance in blocks", pl -> pl.fallDistance));
		t.add(p("IsSprinting", Boolean.class, false, "True while sprinting", LocalPlayer::isSprinting));
		t.add(p("IsSneaking", Boolean.class, false, "True while sneaking", LocalPlayer::isShiftKeyDown));
		t.add(p("IsFlying", Boolean.class, false, "True while flying", pl -> pl.getAbilities().flying));
		t.add(p("IsOnGround", Boolean.class, false, "True when standing on the ground", LocalPlayer::onGround));
		t.add(p("IsSwimming", Boolean.class, false, "True while swimming", LocalPlayer::isSwimming));
		t.add(p("IsInWater", Boolean.class, false, "True when in water", LocalPlayer::isInWater));
		t.add(p("IsBurning", Boolean.class, false, "True when on fire", LocalPlayer::isOnFire));
		t.add(p("IsRiding", Boolean.class, false, "True when riding something", LocalPlayer::isPassenger));
		t.add(p("ChunkX", Integer.class, 0, "X of the chunk you are in", pl -> pl.blockPosition().getX() >> 4));
		t.add(p("ChunkZ", Integer.class, 0, "Z of the chunk you are in", pl -> pl.blockPosition().getZ() >> 4));
		t.add(p("Effects", String.class, "", "Your status effects, one per line in their colour: name, level and time left (empty when none)",
				MinecraftTags::effects));
		t.add(p("EffectCount", Integer.class, 0, "How many status effects you have", pl -> pl.getActiveEffects().size()));
		t.add(p("PlayerName", String.class, "", "Your player name", pl -> pl.getName().getString()));
		t.add(v("GameMode", String.class, "Current game mode",
				() -> mc().gameMode == null ? "" : mc().gameMode.getPlayerMode().getLongDisplayName().getString()));

		// world
		t.add(v("Biome", String.class, "Biome you are in", () -> {
			if (mc().level == null || player() == null) {
				return "";
			}
			return mc().level.getBiome(player().blockPosition()).unwrapKey()
					.map(k -> Component.translatable("biome." + k.identifier().getNamespace() + "." + k.identifier().getPath()).getString())
					.orElse("");
		}));
		t.add(v("Dimension", String.class, "Dimension id (overworld, the_nether, the_end)",
				() -> mc().level == null ? "" : mc().level.dimension().identifier().getPath()));
		t.add(v("Light", Integer.class, "Light level where you stand",
				() -> mc().level == null || player() == null ? 0 : mc().level.getMaxLocalRawBrightness(player().blockPosition())));
		t.add(v("SkyLight", Integer.class, "Sky light level where you stand",
				() -> mc().level == null || player() == null ? 0 : mc().level.getEffectiveSkyBrightness(player().blockPosition())));
		t.add(v("DayTicks", Long.class, "Time of day in ticks (0-23999)", () -> mc().level == null ? 0L : mc().level.getOverworldClockTime() % 24000));
		t.add(v("WorldDay", Long.class, "In-game day number", () -> mc().level == null ? 0L : mc().level.getOverworldClockTime() / 24000 + 1));
		t.add(v("WorldTime", String.class, "In-game clock (HH:mm)", () -> {
			long ticks = mc().level == null ? 6000 : mc().level.getOverworldClockTime() % 24000;
			int hour = (int) ((ticks / 1000 + 6) % 24);
			int minute = (int) (ticks % 1000 * 60 / 1000);
			return String.format("%02d:%02d", hour, minute);
		}));
		t.add(v("IsDayTime", Boolean.class, "True during the in-game day", () -> mc().level != null && mc().level.isBrightOutside()));
		t.add(v("IsRaining", Boolean.class, "True while it rains", () -> mc().level != null && mc().level.isRaining()));
		t.add(v("IsThundering", Boolean.class, "True during a thunderstorm", () -> mc().level != null && mc().level.isThundering()));
		t.add(v("Entities", Integer.class, "Number of loaded entities", () -> mc().level == null ? 0 : mc().level.getEntityCount()));
		t.add(v("TargetBlockId", String.class, "Id of the block you are looking at, for block displays", () -> {
			HitResult hit = mc().hitResult;
			if (mc().level == null || !(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
				return "";
			}
			return BuiltInRegistries.BLOCK.getKey(mc().level.getBlockState(bhr.getBlockPos()).getBlock()).toString();
		}));
		t.add(v("TargetBlock", String.class, "Name of the block you are looking at", () -> {
			HitResult hit = mc().hitResult;
			if (mc().level == null || !(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
				return "";
			}
			return mc().level.getBlockState(bhr.getBlockPos()).getBlock().getName().getString();
		}));
		t.add(v("TargetEntity", String.class, "Name of the entity you are looking at",
				() -> mc().hitResult instanceof EntityHitResult ehr ? ehr.getEntity().getName().getString() : ""));
		t.add(v("TargetPos", String.class, "Position of the block you are looking at", () -> {
			HitResult hit = mc().hitResult;
			if (!(hit instanceof BlockHitResult bhr) || hit.getType() != HitResult.Type.BLOCK) {
				return "";
			}
			BlockPos pos = bhr.getBlockPos();
			return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
		}));

		// input (Minecraft key bindings)
		t.add(v("Cps", Integer.class, "Left clicks per second", Trackers::leftCps));
		t.add(v("RightCps", Integer.class, "Right clicks per second", Trackers::rightCps));
		t.add(Def.of("IsBindHeld", Boolean.class).param("bind", String.class)
				.desc("True while a key binding is held: forward, back, left, right, jump, sneak, sprint, attack, use\nEx) {IsBindHeld:jump}")
				.fn(a -> {
					KeyMapping k = keyMapping((String) a[0]);
					return k != null && k.isDown();
				}));
		t.add(Def.of("BindKey", String.class).param("bind", String.class)
				.desc("The key assigned to a binding\nEx) {BindKey:forward}")
				.fn(a -> {
					KeyMapping k = keyMapping((String) a[0]);
					return k == null ? "" : k.getTranslatedKeyMessage().getString();
				}));

		// items
		itemTags(t, "Held", LocalPlayer::getMainHandItem);
		itemTags(t, "Offhand", LocalPlayer::getOffhandItem);
		t.add(Def.of("ItemCount", Integer.class).param("id", String.class)
				.desc("How many of an item you carry\nEx) {ItemCount:arrow}")
				.fn(a -> {
					LocalPlayer pl = player();
					Item item = item((String) a[0]);
					if (pl == null || item == null) {
						return 0;
					}
					int count = 0;
					var inv = pl.getInventory();
					for (int i = 0; i < inv.getContainerSize(); i++) {
						ItemStack stack = inv.getItem(i);
						if (stack.getItem() == item) {
							count += stack.getCount();
						}
					}
					return count;
				}));
		t.add(Def.of("SlotItem", String.class).opt("slot", String.class, "mainhand")
				.desc("Id of the item in a slot, for item displays: mainhand, offhand, head, chest, legs, feet, or an inventory index (0–8 is the hotbar)\nEx) {SlotItem:head}")
				.fn(a -> {
					LocalPlayer pl = player();
					ItemStack s = pl == null ? ItemStack.EMPTY : slot(pl, (String) a[0]);
					return s.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
				}));
		t.add(Def.of("ArmorDurability", Double.class).format().param("slot", String.class)
				.desc("Durability % of an armour slot: head, chest, legs, feet\nEx) {ArmorDurability:head,0}")
				.fn(a -> {
					LocalPlayer pl = player();
					if (pl == null) {
						return 0d;
					}
					EquipmentSlot slot = switch (((String) a[0]).toLowerCase(Locale.ROOT)) {
						case "head", "helmet" -> EquipmentSlot.HEAD;
						case "chest", "chestplate" -> EquipmentSlot.CHEST;
						case "legs", "leggings" -> EquipmentSlot.LEGS;
						default -> EquipmentSlot.FEET;
					};
					return percent(pl.getItemBySlot(slot));
				}));

		// server / session
		t.add(v("ServerIp", String.class, "Server address (or Singleplayer)", () -> {
			ServerData s = mc().getCurrentServer();
			return s == null ? Component.translatable("label.customhud.singleplayer").getString() : s.ip;
		}));
		t.add(v("ServerName", String.class, "Server name from your server list", () -> {
			ServerData s = mc().getCurrentServer();
			return s == null ? Component.translatable("label.customhud.singleplayer").getString() : s.name;
		}));
		t.add(v("IsSingleplayer", Boolean.class, "True in singleplayer", () -> mc().getCurrentServer() == null));
		t.add(v("PlayerCount", Integer.class, "Players online", () -> mc().getConnection() == null ? 0 : mc().getConnection().getOnlinePlayers().size()));
		t.add(v("SessionTime", String.class, "Time since joining (H:mm:ss)", () -> {
			long sec = Trackers.sessionMillis() / 1000;
			return String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60);
		}));
		t.add(v("SessionSeconds", Double.class, "Seconds since joining", () -> Trackers.sessionMillis() / 1000.0));
		t.add(v("IsPaused", Boolean.class, "True while the game is paused", () -> mc().isPaused()));
		t.add(v("IsInWorld", Boolean.class, "True while playing in a world", () -> mc().level != null));
		return t;
	}

	private static final String[] ROMAN = {"", "", " II", " III", " IV", " V", " VI", " VII", " VIII", " IX", " X"};

	/** {@code <color=#7CAFC6>Speed II</color> <color=#AAAAAA>1:23</color>} per effect, the shortest first. */
	private static String effects(LocalPlayer pl) {
		List<MobEffectInstance> list = new ArrayList<>(pl.getActiveEffects());
		list.sort(Comparator.comparingInt(e -> e.isInfiniteDuration() ? Integer.MAX_VALUE : e.getDuration()));
		StringBuilder sb = new StringBuilder();
		for (MobEffectInstance e : list) {
			if (!sb.isEmpty()) {
				sb.append('\n');
			}
			int level = e.getAmplifier() + 1;
			String name = Component.translatable(e.getDescriptionId()).getString() + (level < ROMAN.length ? ROMAN[level] : " " + level);
			String time;
			if (e.isInfiniteDuration()) {
				time = "∞";
			} else {
				int sec = e.getDuration() / 20;
				time = sec >= 3600 ? String.format("%d:%02d:%02d", sec / 3600, sec / 60 % 60, sec % 60) : String.format("%d:%02d", sec / 60, sec % 60);
			}
			int rgb = e.getEffect().value().getColor() & 0xFFFFFF;
			sb.append(String.format("<color=#%06X>%s</color> <color=#AAAAAA>%s</color>", rgb, name, time));
		}
		return sb.toString();
	}

	private static double otherFactor(LocalPlayer pl) {
		return pl.level().dimension() == Level.NETHER ? 8 : 1 / 8.0;
	}

	private static String axis(Direction dir) {
		return switch (dir) {
			case NORTH -> "-Z";
			case SOUTH -> "+Z";
			case WEST -> "-X";
			case EAST -> "+X";
			case UP -> "+Y";
			case DOWN -> "-Y";
		};
	}

	private static KeyMapping keyMapping(String name) {
		var o = mc().options;
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "forward", "w", "up" -> o.keyUp;
			case "back", "s", "down" -> o.keyDown;
			case "left", "a" -> o.keyLeft;
			case "right", "d" -> o.keyRight;
			case "jump", "space" -> o.keyJump;
			case "sneak", "shift" -> o.keyShift;
			case "sprint" -> o.keySprint;
			case "attack", "lmb" -> o.keyAttack;
			case "use", "rmb" -> o.keyUse;
			default -> null;
		};
	}

	private static void itemTags(List<TagCore> t, String prefix, Function<LocalPlayer, ItemStack> stack) {
		t.add(p(prefix + "Item", String.class, "", "Name of the item in your " + hand(prefix),
				pl -> stack.apply(pl).isEmpty() ? "" : stack.apply(pl).getHoverName().getString()));
		t.add(p(prefix + "Count", Integer.class, 0, "Stack size in your " + hand(prefix), pl -> stack.apply(pl).getCount()));
		t.add(p(prefix + "Durability", Integer.class, 0, "Durability left of the item in your " + hand(prefix), pl -> {
			ItemStack s = stack.apply(pl);
			return s.isDamageableItem() ? s.getMaxDamage() - s.getDamageValue() : 0;
		}));
		t.add(p(prefix + "MaxDurability", Integer.class, 0, "Maximum durability of the item in your " + hand(prefix) + " (0 if none)", pl -> {
			ItemStack s = stack.apply(pl);
			return s.isDamageableItem() ? s.getMaxDamage() : 0;
		}));
		t.add(p(prefix + "DurabilityPercent", Double.class, 0d, "Durability in percent of the item in your " + hand(prefix),
				pl -> percent(stack.apply(pl))));
	}

	private static String hand(String prefix) {
		return prefix.equals("Held") ? "main hand" : "off hand";
	}

	/** mainhand, offhand, head, chest, legs, feet, or an inventory index. */
	private static ItemStack slot(LocalPlayer pl, String name) {
		String n = name == null ? "mainhand" : name.trim().toLowerCase(Locale.ROOT);
		return switch (n) {
			case "", "mainhand", "hand", "main" -> pl.getMainHandItem();
			case "offhand", "off" -> pl.getOffhandItem();
			case "head", "helmet" -> pl.getItemBySlot(EquipmentSlot.HEAD);
			case "chest", "chestplate" -> pl.getItemBySlot(EquipmentSlot.CHEST);
			case "legs", "leggings" -> pl.getItemBySlot(EquipmentSlot.LEGS);
			case "feet", "boots" -> pl.getItemBySlot(EquipmentSlot.FEET);
			default -> {
				try {
					int i = Integer.parseInt(n);
					yield i >= 0 && i < pl.getInventory().getContainerSize() ? pl.getInventory().getItem(i) : ItemStack.EMPTY;
				} catch (NumberFormatException e) {
					yield ItemStack.EMPTY;
				}
			}
		};
	}

	private static double percent(ItemStack s) {
		return s.isDamageableItem() ? 100.0 * (s.getMaxDamage() - s.getDamageValue()) / s.getMaxDamage() : 0;
	}

	private static Item item(String id) {
		Identifier key = Identifier.tryParse(id.contains(":") ? id : "minecraft:" + id);
		if (key == null) {
			return null;
		}
		Item item = BuiltInRegistries.ITEM.getValue(key);
		return item == Items.AIR ? null : item;
	}
}
