package fastloader.newgen.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Item block with per-meta names, used by the multi-variant fallback blocks. */
public class NGItemBlock extends ItemBlock {

    public NGItemBlock(Block block) {
        super(block);
        setHasSubtypes(true);
        setMaxDamage(0);
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int damage) {
        return field_150939_a.getIcon(2, damage);
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        String[] names = null;
        if (field_150939_a instanceof NGBlockSimple s) names = s.getNames();
        else if (field_150939_a instanceof NGBlockPlant p) names = p.getNames();
        if (names != null) return "tile.fastloader." + names[stack.getItemDamage() % names.length];
        return super.getUnlocalizedName(stack);
    }
}
