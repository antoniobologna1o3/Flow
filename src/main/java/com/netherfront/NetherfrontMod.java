package com.netherfront;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

@Mod(NF.MOD_ID)
public class NetherfrontMod {
    public static final Logger LOGGER = LogUtils.getLogger();

    public NetherfrontMod() {
        LOGGER.info("{} initialising", NF.MOD_NAME);
    }
}
