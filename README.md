# Horror Mod – Minecraft 1.20.1 Fabric

Mod horror atmospheric untuk Minecraft 1.20.1 berbasis Fabric.

## Gameplay

### Fase 1 – Penantian (Overworld)
Saat player **join server**, tidak ada yang terjadi selama **2 menit**.  
Setelah ~30 detik, akan terdengar suara radio glitch pelan sebagai foreshadowing.

### Fase 2 – Dimensi Hutan Void (`horrormod:void_forest`)
Setelah 2 menit, player otomatis **diteleport** ke dimensi custom berisi:
- Lantai **grass block** (2 lapis: bedrock + grass) — flat sempurna
- **Pohon oak** tertata rapi setiap 8 block
- **Kabut tebal** (fog effect dari dimensi the_end)
- Area terbatas **64×64 block**
- Musik ambient horror terus berputar

Jika player **menyentuh batas 64×64** atau jatuh ke void → diteleport kembali ke overworld ke posisi semula.

### Fase 3 – Kembali ke Overworld + Salib
Setelah kembali, sebuah **struktur salib** (dark oak log) akan spawn **±20 block** dari posisi player.  
Suara distorted radio akan berbunyi.

**Hancurkan salibnya** untuk memicu Fase 4.

### Fase 4 – Dimensi Planks (`horrormod:planks_dimension`)
Player diteleport ke dimensi gelap berisi:
- **Lantai oak planks** tak terbatas
- **Langit-langit oak planks** hanya **3 block di atas lantai** — terasa sempit
- Suara ambient whale/horror terus berbunyi

Player harus **berjalan 70 block** untuk menemukan tangga yang muncul otomatis.

### Fase 5 – Tangga & Finale
Setelah berjalan 70 block, **tangga oak** muncul membawa player ke atas.  
Naik tangga → teleport kembali ke overworld dengan **banyak salib** yang spawn di sekitar player.

---

## Sound Effects
| File | Digunakan saat |
|------|---------------|
| `horror_ambient.ogg` | Dimensi hutan void (loop) |
| `void_ambiance.ogg` | Dimensi planks (loop) |
| `radio_glitch.ogg` | Foreshadowing & transisi |

---

## Build

### Via GitHub Actions (Recommended)
1. Fork/push repo ini ke GitHub
2. Actions tab → pilih workflow **Build Horror Mod**
3. Klik **Run workflow**
4. Download artifact JAR dari tab Actions setelah selesai

### Build Lokal
Requirements: **JDK 17**, **Gradle 8.6**

```bash
./gradlew build
# Output: build/libs/horrormod-1.0.0.jar
```

---

## Instalasi
1. Install **Fabric Loader 0.15+** untuk Minecraft 1.20.1
2. Install **Fabric API**
3. Taruh `horrormod-1.0.0.jar` ke folder `mods/`
4. Jalankan server atau singleplayer

---

## Kompatibilitas
- Minecraft: **1.20.1**
- Loader: **Fabric 0.15.11**
- Side: **Server + Client**
