package jackdaw.applecrates.block;

import jackdaw.applecrates.block.blockentity.CrateBE;
import jackdaw.applecrates.container.CrateMenu;
import jackdaw.applecrates.registry.GeneralRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import static jackdaw.applecrates.container.CrateStackHandler.TAGSTOCK;

public class CrateBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);

    private final WoodType type;

    public CrateBlock(WoodType type) {
        super(Properties.copy(Blocks.OAK_PLANKS).noOcclusion().isValidSpawn(CrateBlock::never).isRedstoneConductor(CrateBlock::never).isSuffocating(CrateBlock::never).isViewBlocking(CrateBlock::never));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
        this.type = type;
    }

    private static boolean never(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos) {
        return false;
    }

    private static Boolean never(BlockState state, BlockGetter getter, BlockPos pos, EntityType<?> type) {
        return false;
    }


    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter getter, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> stateDef) {
        stateDef.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext placeContext) {
        return this.defaultBlockState().setValue(FACING, placeContext.getHorizontalDirection());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return GeneralRegistry.BE_MAP.get(type).get().create(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public void setPlacedBy(Level pLevel, BlockPos pPos, BlockState pState, @Nullable LivingEntity pPlacer, ItemStack pStack) {
        super.setPlacedBy(pLevel, pPos, pState, pPlacer, pStack);
        if (pPlacer instanceof ServerPlayer serverPlayer && pLevel.getBlockEntity(pPos) instanceof CrateBE crate) {
            crate.setOwner(serverPlayer);
        }
    }

    @Override
    public InteractionResult use(BlockState pState, Level pLevel, BlockPos pPos, Player pPlayer, InteractionHand pHand, BlockHitResult pHit) {
        if (pLevel.getBlockEntity(pPos) instanceof CrateBE crate && pHand.equals(InteractionHand.MAIN_HAND)) {
            boolean owner =
                    crate.isOwner(pPlayer) || pPlayer.isCreative() && pPlayer.isShiftKeyDown(); //add creative debug testing
            if (pPlayer instanceof ServerPlayer sp)
                NetworkHooks.openGui(sp, new SimpleMenuProvider((pContainerId, pInventory, pPlayer1) ->
                        new CrateMenu(owner ? GeneralRegistry.CRATE_MENU_OWNER.get() : GeneralRegistry.CRATE_MENU_BUYER.get(), pContainerId, pInventory, crate, owner),
                        new TranslatableComponent("container.crate" + (owner ? ".owner" : ""))));
            pLevel.playSound(pPlayer, pPos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            return InteractionResult.sidedSuccess(pLevel.isClientSide);
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void onRemove(BlockState pState, Level pLevel, BlockPos pPos, BlockState pNewState, boolean pIsMoving) {
        if (!pState.is(pNewState.getBlock())) {
            if (pLevel.getBlockEntity(pPos) instanceof CrateBE crate && pLevel instanceof ServerLevel serverLevel) {
                for (int i = 0; i < crate.crateStock.getSlots(); i++) {
                    ItemStack stack = crate.crateStock.getStackInSlot(i);
                    if (i == 29) {
                        if (!stack.isEmpty() && stack.hasTag() && stack.getTag().contains(TAGSTOCK)) {
                            int pay = stack.getTag().getInt(TAGSTOCK);
                            ItemStack prepCopy = stack.copy();
                            prepCopy.removeTagKey(TAGSTOCK);
                            if (prepCopy.getTag() != null && prepCopy.getTag().isEmpty())
                                prepCopy.setTag(null);

                            while (pay > 0) {
                                ItemStack toDrop = prepCopy.copy();
                                if (pay >= prepCopy.getMaxStackSize()) {
                                    toDrop.setCount(prepCopy.getMaxStackSize());
                                    pay -= prepCopy.getMaxStackSize();
                                } else {
                                    toDrop.setCount(pay);
                                    pay = 0; //set to 0. we could count down the last items from the counter, but it's the same
                                }
                                Containers.dropItemStack(serverLevel, pPos.getX(), pPos.getY(), pPos.getZ(), toDrop);
                            }
                        }
                    } else if (!stack.isEmpty()) {
                        Containers.dropItemStack(serverLevel, pPos.getX(), pPos.getY(), pPos.getZ(), stack);
                    }
                }
                for (int i = 0; i < 2; i++) {
                    ItemStack toDrop = crate.interactable.getStackInSlot(i);
                    Containers.dropItemStack(serverLevel, pPos.getX(), pPos.getY(), pPos.getZ(), toDrop);
                }
                pLevel.updateNeighbourForOutputSignal(pPos, this);
            }

            super.onRemove(pState, pLevel, pPos, pNewState, pIsMoving);
        }
    }

    //only owner can break
    @Override
    public float getDestroyProgress(BlockState pState, Player pPlayer, BlockGetter pLevel, BlockPos pPos) {
        if (pLevel.getBlockEntity(pPos) instanceof CrateBE crate && crate.isOwner(pPlayer))
            return super.getDestroyProgress(pState, pPlayer, pLevel, pPos);
        return 0;
    }
}
