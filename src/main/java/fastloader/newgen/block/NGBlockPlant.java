package fastloader.newgen.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBush;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.EnumPlantType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fallback nether plant (fungi, roots, sprouts). Grows on nylium, netherrack and soul soil. */
public class NGBlockPlant extends BlockBush {

    private final String[] names;
    @SideOnly(Side.CLIENT)
    private IIcon[] icons;

    public NGBlockPlant(String... names) {
        super(Material.plants);
        this.names = names;
        setCreativeTab(CreativeTabs.tabDecorations);
        setBlockName("fastloader." + names[0]);
        setStepSound(soundTypeGrass);
        setHardness(0.0F);
        float f = 0.375F;
        setBlockBounds(0.5F - f, 0.0F, 0.5F - f, 0.5F + f, 0.6F, 0.5F + f);
    }

    public String[] getNames() {
        return names;
    }

    public static boolean isNetherSoil(Block b) {
        return b == Blocks.netherrack || b == Blocks.soul_sand
            || b == Blocks.mycelium
            || b == Blocks.dirt
            || b == Blocks.grass
            || b == NGBlocks.CRIMSON_NYLIUM.block
            || b == NGBlocks.SOUL_SOIL.block;
    }

    @Override
    protected boolean canPlaceBlockOn(Block b) {
        return isNetherSoil(b);
    }

    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        return isNetherSoil(world.getBlock(x, y - 1, z));
    }

    @Override
    public EnumPlantType getPlantType(IBlockAccess world, int x, int y, int z) {
        return EnumPlantType.Nether;
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[names.length];
        for (int i = 0; i < names.length; i++) icons[i] = reg.registerIcon("fastloader:" + names[i]);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return icons[meta % names.length];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < names.length; i++) list.add(new ItemStack(item, 1, i));
    }
}
