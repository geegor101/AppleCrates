package jackdaw.applecrates.datagen;

import jackdaw.applecrates.AppleCrates;
import jackdaw.applecrates.registry.GeneralRegistry;
import net.minecraft.data.DataGenerator;
import net.minecraftforge.common.data.LanguageProvider;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CrateLanguage extends LanguageProvider {
    public CrateLanguage(DataGenerator gen, String locale) {
        super(gen, AppleCrates.MODID, locale);
    }

    @Override
    protected void addTranslations() {

        GeneralRegistry.BLOCK_MAP.forEach((woodType, blockRegistryObject) -> {
            String capitalized = Stream.of((woodType.name() + "_crate").replace("_", " ").trim().split("\\s"))
                    .filter(word -> word.length() > 0)
                    .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                    .collect(Collectors.joining(" "));

            add(blockRegistryObject.get(), capitalized);
        });

        add("container.crate", "Crate");
        add("container.crate.owner", "My Crate Shop");

    }
}
