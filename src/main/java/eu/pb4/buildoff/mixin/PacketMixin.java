package eu.pb4.buildoff.mixin;

import eu.pb4.buildoff.MarkedPacket;
import net.minecraft.network.packet.s2c.play.EntityEquipmentUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({EntityEquipmentUpdateS2CPacket.class})
public class PacketMixin implements MarkedPacket {
    @Unique boolean boIsMarked = false;

    @Override
    public boolean bo_isMarked() {
        return this.boIsMarked;
    }

    @Override
    public void bo_mark() {
        this.boIsMarked = true;
    }
}
