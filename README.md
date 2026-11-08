<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHTweaks

*Petits réglages de gameplay d'Opale, un module par réglage.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale-3aa655?style=for-the-badge" alt="SkyHand Opale">

</div>

---

## Présentation

Chaque réglage est un module qui s'active ou se coupe dans `config.yml` et se recharge sans redémarrage.

| Module | Effet | Remplace |
|---|---|---|
| `indestructible-items` | Items au sol indestructibles (lave, feu, explosions), sauf ceux exclus | SHNoLavaBurn |
| `stacked-sheep` | Pas de tonte des moutons stackés, à la main ou au distributeur (WildStacker) | SHNoShearStacked |
| `infinite-anvils` | Enclumes qui ne s'usent pas dans certains mondes | SHInfiniteAnvil |
| `twerk-crops` | S'accroupir fait pousser les cultures proches | SHTwerkCrops |
| `sculk-confirm` | Confirmation avant de casser un catalyseur de sculk sans Toucher de soie | SHVerifSculk |
| `amethyst-nerf` | Quantité d'éclats lâchés par une grappe d'améthyste | SHNerfAmethyst |
| `villagers` | Villageois sans IA, sans commerce, sans reproduction ni golems | SHVillagerNerf |
| `sign-edit` | Modifier une pancarte déjà écrite : VIP, sur son île | SHSignEdit |
| `door-reset` | Portes, trappes et portillons qui reprennent leur état, doubles portes synchronisées | SHInteractReset |

Les permissions des anciens plugins sont gardées telles quelles (`shsignedit.use`, `shsignedit.bypass`, `shinteractreset.bypass`).

Utilisé sur : **Opale**.

## Dépendances

Optionnelles : `WildStacker` (module `stacked-sheep`), `SuperiorSkyblock2` (module `sign-edit`). Sans eux, le module concerné reste coupé.

## Commandes

| Commande | Permission | Effet |
|---|---|---|
| `/shtweaks reload` | `shtweaks.admin` (op) | Recharge les fichiers et relance les modules |
| `/shtweaks modules` | `shtweaks.admin` (op) | Liste les modules et leur état |

## Configuration

Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHTweaks/
├── <a href="src/main/resources/config.yml">config.yml</a>     un bloc par module
└── <a href="src/main/resources/messages.yml">messages.yml</a>   messages du chat
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
