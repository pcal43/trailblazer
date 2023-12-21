package net.pcal.trailblazer.mixins;

import net.minecraft.world.entity.Entity;
import net.pcal.trailblazer.TrailblazerService;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.spongepowered.asm.mixin.injection.At.Shift.BEFORE;

@Mixin(Entity.class)
public class EntityMixin {

    //@Shadow
    //private BlockPos blockPos;

    // get notified any time an entity's blockPos is updated
    @Inject(method = "setPosRaw(DDD)V", at = @At(value = "FIELD", shift = BEFORE, opcode = Opcodes.PUTFIELD, target = "Lnet/minecraft/world/entity/Entity;blockPosition:Lnet/minecraft/core/BlockPos;"))
    void _entity_blockPos_update(double x, double y, double z, CallbackInfo ci) {
        final Entity entity = (Entity)(Object)this;
        if (entity.level().isClientSide()) return; // only process on the server
        TrailblazerService.getInstance().entitySteppingOnBlock(entity, x, y, z);
    }
}