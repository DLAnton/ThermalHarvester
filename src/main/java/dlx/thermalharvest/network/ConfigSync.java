package dlx.thermalharvest.network;

import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.init.THConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

import java.util.HashMap;
import java.util.Map;

/**
 * Синхронизация серверных настроек с клиентом при входе игрока: список удобрений и значения,
 * от которых зависит то, что клиент показывает (подсказки, шкалы, зона, HEI).
 * Без этого на сервере с изменённым конфигом клиент показывал бы свои значения и не пускал бы в ячейку
 * удобрения, которых нет в его локальном списке. При выходе с сервера клиент возвращается к своему конфигу.
 */
public final class ConfigSync {

    public static final ConfigSync INSTANCE = new ConfigSync();
    public static SimpleNetworkWrapper NETWORK;
    /** Клиент сейчас работает на значениях, присланных удалённым сервером. */
    public static boolean remoteValues;

    private ConfigSync() {}

    public static void init() {
        NETWORK = NetworkRegistry.INSTANCE.newSimpleChannel(ThermalHarvest.MOD_ID);
        NETWORK.registerMessage(Handler.class, Message.class, 0, Side.CLIENT);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            NETWORK.sendTo(Message.fromConfig(), (EntityPlayerMP) event.player);
        }
    }

    public static final class Message implements IMessage {

        private final Map<String, THConfig.Fertilizer> fertilizers = new HashMap<>();
        private int capacity;
        private int recyclerPerAugment;
        private int recyclerMax;
        private int catalystBonus;
        private int baseRadius;
        private int maxRadius;
        private int tankCapacity;
        private int[] columns = new int[0];

        public Message() {}

        static Message fromConfig() {
            Message m = new Message();
            m.fertilizers.putAll(THConfig.FERTILIZERS);
            m.capacity = THConfig.fertilizerCapacity;
            m.recyclerPerAugment = THConfig.recyclerChancePerAugment;
            m.recyclerMax = THConfig.recyclerChanceMax;
            m.catalystBonus = THConfig.catalystBonus;
            m.baseRadius = THConfig.baseRadius;
            m.maxRadius = THConfig.maxRadius;
            m.tankCapacity = THConfig.tankCapacity;
            m.columns = THConfig.columnsPerSecond.clone();
            return m;
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(capacity);
            buf.writeInt(recyclerPerAugment);
            buf.writeInt(recyclerMax);
            buf.writeInt(catalystBonus);
            buf.writeInt(baseRadius);
            buf.writeInt(maxRadius);
            buf.writeInt(tankCapacity);
            buf.writeByte(columns.length);
            for (int c : columns) {
                buf.writeInt(c);
            }
            buf.writeInt(fertilizers.size());
            for (Map.Entry<String, THConfig.Fertilizer> e : fertilizers.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, e.getKey());
                buf.writeInt(e.getValue().multiplier);
                buf.writeInt(e.getValue().units);
            }
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            capacity = buf.readInt();
            recyclerPerAugment = buf.readInt();
            recyclerMax = buf.readInt();
            catalystBonus = buf.readInt();
            baseRadius = buf.readInt();
            maxRadius = buf.readInt();
            tankCapacity = buf.readInt();
            columns = new int[buf.readUnsignedByte()];
            for (int i = 0; i < columns.length; i++) {
                columns[i] = buf.readInt();
            }
            int n = buf.readInt();
            for (int i = 0; i < n; i++) {
                String key = ByteBufUtils.readUTF8String(buf);
                fertilizers.put(key, new THConfig.Fertilizer(buf.readInt(), buf.readInt()));
            }
        }

        void apply() {
            THConfig.FERTILIZERS.clear();
            THConfig.FERTILIZERS.putAll(fertilizers);
            THConfig.fertilizerCapacity = capacity;
            THConfig.recyclerChancePerAugment = recyclerPerAugment;
            THConfig.recyclerChanceMax = recyclerMax;
            THConfig.catalystBonus = catalystBonus;
            THConfig.baseRadius = baseRadius;
            THConfig.maxRadius = maxRadius;
            THConfig.tankCapacity = tankCapacity;
            if (columns.length == THConfig.columnsPerSecond.length) {
                THConfig.columnsPerSecond = columns;
            }
            remoteValues = true;
            THConfig.fireChanged();
        }
    }

    public static final class Handler implements IMessageHandler<Message, IMessage> {

        @Override
        public IMessage onMessage(Message message, MessageContext ctx) {
            if (FMLCommonHandler.instance().getMinecraftServerInstance() != null) {
                return null; // одиночная игра / хост LAN: сервер в этом же процессе, настройки и так общие
            }
            FMLCommonHandler.instance().getWorldThread(ctx.netHandler).addScheduledTask(message::apply);
            return null;
        }
    }
}
