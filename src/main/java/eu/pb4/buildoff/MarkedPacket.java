package eu.pb4.buildoff;

import net.minecraft.network.Packet;

public interface MarkedPacket {
    boolean bo_isMarked();

    void bo_mark();


    static boolean is(Packet<?> packet) {
        if (packet instanceof MarkedPacket markedPacket) {
            return markedPacket.bo_isMarked();
        }
        return false;
    }

    static <T extends Packet<?>> T mark(T packet) {
        ((MarkedPacket) packet).bo_mark();
        return packet;
    }
}
