package fastloader.newgen.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fallback weeping (hanging, {@code up=false}) or twisting (standing, {@code up=true}) vines. Climbable. */
public class NGBlockVines extends Block {

    private final boolean up;
    private final String name;
    @SideOnly(Side.CLIENT)
    private IIcon plant;

    public NGBlockVines(String name, boolean up) {
        super(Material.plants);
        this.name = name;
        this.up = up;
        setCreativeTab(CreativeTabs.tabDecorations);
        setBlockName("fastloader." + name);
        setStepSound(soundTypeGrass);
        setHardness(0.0F);
        setBlockBounds(0.25F, 0.0F, 0.25F, 0.75F, 1.0F, 0.75F);
    }

    private int anchorY(int y) {
        return up ? y - 1 : y + 1;
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        Block b = world.getBlock(x, anchorY(y), z);
        return b == this || b.isOpaqueCube();
    }

    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        return canPlaceBlockAt(world, x, y, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        if (!canBlockStay(world, x, y, z)) {
            dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public boolean isLadder(IBlockAccess world, int x, int y, int z, net.minecraft.entity.EntityLivingBase entity) {
        return true;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return null;
    }

    @Override
    public void onEntityCollidedWithBlock(World world, int x, int y, int z, Entity entity) {
        entity.fallDistance = 0;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return 1;
    }

    @Override
    public int quantityDropped(Random random) {
        return random.nextInt(3) == 0 ? 1 : 0;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon("fastloader:" + name);
        plant = reg.registerIcon("fastloader:" + name + "_plant");
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(IBlockAccess world, int x, int y, int z, int side) {
        // the tip uses the "head" texture, everything else the plant body texture
        int next = up ? y + 1 : y - 1;
        return world.getBlock(x, next, z) == this ? plant : blockIcon;
    }
}
