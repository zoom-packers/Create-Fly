/*
 * Copyright (c) 2016, 2017, 2018, 2019 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.zurrtum.create.mixin;

import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CreativeModeTabs.class)
public class CreativeModeTabsMixin {
    @Inject(method = "validate", at = @At("HEAD"), cancellable = true)
    private static void deferDuplicateCheck(CallbackInfo ci) {
        /*
         * Create registers its tabs during vanilla bootstrap, where vanilla's position check would reject them (they
         * share NeoForge's default top row, column 0). NeoForge: Fabric's pagination (paginateGroups) is gone, since
         * NeoForge pages the creative tabs itself and places each tab by its page, not by its row and column.
         */
        ci.cancel();
    }
}
