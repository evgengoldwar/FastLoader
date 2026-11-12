package fastloader.newgen.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Fallback full cube with per-meta textures. A texture name may be suffixed in the textures folder with
 * "_top"/"_side" when {@link #topSide} is set (e.g. nylium).
 */
public class NGBlockSimple extends Block {

    protected final String[] names;
    protected final boolean topSide;
    @SideOnly(Side.CLIENT)
    protected IIcon[] icons, tops;
    protected Block dropOverride;

    public NGBlockSimple(Material material, boolean topSide, String... names) {
        super(material);
        this.names = names;
        this.topSide = topSide;
        setCreativeTab(CreativeTabs.tabBlock);
        setBlockName("fastloader." + names[0]);
    }

    public NGBlockSimple setDrop(Block drop) {
        this.dropOverride = drop;
        return this;
    }

    public String[] getNames() {
        return names;
    }

    @Override
    public Item getItemDropped(int meta, java.util.Random rand, int fortune) {
        return dropOverride != null ? Item.getItemFromBlock(dropOverride) : super.getItemDropped(meta, rand, fortune);
    }

    @Override
    public int damageDropped(int meta) {
        return dropOverride != null ? 0 : meta;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        icons = new IIcon[names.length];
        tops = new IIcon[names.length];
        for (int i = 0; i < names.length; i++) {
            if (topSide) {
                icons[i] = reg.registerIcon("fastloader:" + names[i] + "_side");
                tops[i] = reg.registerIcon("fastloader:" + names[i] + "_top");
            } else {
                icons[i] = tops[i] = reg.registerIcon("fastloader:" + names[i]);
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        int m = meta % names.length;
        if (topSide) {
            if (side == 1) return tops[m];
            if (side == 0) return net.minecraft.init.Blocks.netherrack.getIcon(0, 0);
        }
        return icons[m];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int i = 0; i < names.length; i++) list.add(new ItemStack(item, 1, i));
    }
}
