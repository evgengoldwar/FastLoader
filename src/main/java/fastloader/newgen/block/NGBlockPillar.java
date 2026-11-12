package fastloader.newgen.block;

import net.minecraft.block.BlockRotatedPillar;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Fallback axis-rotatable block (nether stems, basalt, bone block). */
public class NGBlockPillar extends BlockRotatedPillar {

    private final String name;
    @SideOnly(Side.CLIENT)
    private IIcon top;

    public NGBlockPillar(Material material, String name) {
        super(material);
        this.name = name;
        setCreativeTab(CreativeTabs.tabBlock);
        setBlockName("fastloader." + name);
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IIcon getSideIcon(int meta) {
        return blockIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    protected IIcon getTopIcon(int meta) {
        return top;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        blockIcon = reg.registerIcon("fastloader:" + name + "_side");
        top = reg.registerIcon("fastloader:" + name + "_top");
    }
}
