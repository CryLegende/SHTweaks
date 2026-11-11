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
| `items` | Items au sol insensibles à la lave, au feu et aux explosions, sauf ceux exclus | SHNoLavaBurn |
| `anvils` | Enclumes qui ne s'usent pas dans certains mondes | SHInfiniteAnvil |
| `crops` | S'accroupir fait pousser les cultures listées | SHTwerkCrops |
| `sculk` | Second coup demandé avant de casser un catalyseur de sculk sans Toucher de soie | SHVerifSculk |
| `amethyst` | Nombre d'éclats lâchés par une grappe d'améthyste | SHNerfAmethyst |
| `signedit` | Modifier une pancarte déjà écrite : VIP, sur son île | SHSignEdit |
| `doors` | Portes, trappes et portillons qui reprennent leur état ; doubles portes et trappes voisines ouvertes ensemble | SHInteractReset |
| `islandbypass` | Mode bypass des îles activé à la connexion pour ceux qui ont `superior.admin.bypass` ; le couper tient jusqu'au prochain redémarrage | — |
| `redstone` | Relance la redstone que SuperiorSkyblock2 a bloquée sur une île sans membre connecté (`disable-redstone-offline`) : sans lui, répéteurs, comparateurs, torches et horloges restent figés au retour du joueur | — |
| `recount` | Recalcule une fois chaque île (comptes de blocs et niveau remis d'accord avec ce qui est posé), pendant qu'un de ses membres s'y trouve, une île à la fois et jamais quand le serveur rame ; îles faites dans `recount.yml` | — |

Permissions au format `shtweaks.<module>.<permission>` : `shtweaks.signedit.use`, `shtweaks.signedit.bypass`, `shtweaks.doors.bypass`.

Remplacés par des réglages natifs plutôt que par un module : la tonte des moutons stackés (WildStacker `stack-split.SHEEP_SHEAR: true`, ex-SHNoShearStacked) et le comportement des villageois (Purpur `allow-trading`, `can-breed`, `cure`, ex-SHVillagerNerf).

Utilisé sur : **Opale**.

## Dépendances

Optionnelle : `SuperiorSkyblock2` (modules `signedit`, `islandbypass`, `redstone` et `recount`, coupés sans lui).

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
