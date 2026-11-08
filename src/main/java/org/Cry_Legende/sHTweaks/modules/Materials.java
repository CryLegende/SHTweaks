package org.Cry_Legende.sHTweaks.modules;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Lecture des listes de matériaux de config.yml ; un nom inconnu est signalé puis ignoré. */
final class Materials {

    private Materials() {
    }

    static Set<Material> parse(List<String> names, Logger logger, String path) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for (String name : names) {
            Material material = Material.matchMaterial(name.trim().toUpperCase(Locale.ROOT));
            if (material == null) {
                logger.warning(path + " : matériau inconnu « " + name + " », ignoré.");
            } else {
                materials.add(material);
            }
        }
        return materials;
    }
}
