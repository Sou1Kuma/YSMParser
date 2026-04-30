# YSMParser

YSMParser 是一个 C++20 命令行工具与 WebAssembly 应用，用于解析 Yes Steve Model (YSM) 模组的 `.ysm` 加密模型文件，将其完整还原为可直接导入 BlockBench 的工程目录。

## 功能

- **全版本覆盖**：支持 YSM V1、V2、V3 三种加密协议，通过文件头自动识别
- **V3 协议族完整解析**：按 `format` 版本（`<4`、`4~15`、`>15`、`≥26`、`≥32`）逐族适配，覆盖历代协议演进
- **多级保护链还原**：魔改 CityHash64 → 变体 XChaCha20 → MT19937_64 XOR → 魔改 Zstd，全链路可复现
- **工程目录导出**：还原 `models/`、`animations/`、`controller/`、`textures/`、`sounds/`、`lang/`、`avatar/` 及 `ysm.json`
- **几何反推**：当模型仅保存顶点/法线/UV 时，自动还原 BlockBench 的 `origin`、`size`、`pivot`、`rotation` 与各面 `uv`
- **多线程批量处理**：递归扫描输入目录，并行解析多个 `.ysm` 文件
- **Web 前端**：支持浏览器拖拽导入 `.ysm` 文件，一键下载 ZIP 结果

## 构建

### 前置条件

- CMake ≥ 3.12
- C++20 编译器（MSVC、GCC、Clang）
- 可选：Emscripten（用于 WASM 构建）

### 本地构建

```bash
# 配置 (Windows x64 Release)
cmake --preset x64-release

# 编译
cmake --build --preset x64-release
```

更多预设见 `CMakePresets.json`：`x64-debug`、`x86-release`、`linux-release`、`macos-release` 等。

### WASM 构建

```bash
# Node.js 环境
cmake --preset wasm-release
cmake --build --preset wasm-release

# Web 浏览器环境
cmake --preset wasm-web-release
cmake --build --preset wasm-web-release
```

WASM 构建需要 `EMSDK` 环境变量指向 Emscripten 安装目录。

## 使用

### 命令行

```bash
YSMParser -i <输入目录> -o <输出目录> [-v] [-j <线程数>]
```

| 参数 | 说明 |
|------|------|
| `-i, --input` | 输入目录（必填），递归扫描其中的 `.ysm` 文件 |
| `-o, --output` | 输出根目录（必填），每个输入文件生成独立子目录 |
| `-v, --verbose` | 详细输出模式（强制单线程） |
| `-j, --threads` | 并行线程数（默认 `0` = 自动检测 CPU 核心数） |
| `--version` | 显示版本号 |

### Web 前端

1. 将 `web/` 目录与 `YSMParser.js`、`YSMParser.wasm` 放置在同一路径下
2. 在浏览器中打开 `index.html`
3. 拖入或选择 `.ysm` 文件
4. 点击 "Start Processing"
5. 完成后点击 "Download ZIP" 下载结果

## 支持的格式

### 版本识别

`YSMParserFactory::Create()` 按以下规则自动分发解析器：

| 文件特征 | 解析器 |
|----------|--------|
| 前 3 字节 = UTF-8 BOM (`EF BB BF`)，偏移 3 处 = `YSGP` | `YSMParserV3` |
| 偏移 0 = `YSGP`，偏移 4 = `0x01` (BE) | `YSMParserV1` |
| 偏移 0 = `YSGP`，偏移 4 = `0x02` (BE) | `YSMParserV2` |

### V1 协议

- 文件头后直接跟 16 字节 AES 密钥
- 每个资源：Base64 文件名 + AES-128-CBC 加密 + zlib 压缩
- 恢复终点即原始资源文件

### V2 协议

- 在 V1 基础上增加 Java `Random` 派生密钥层
- 对密文做 MD5 → 映射为 `JavaRandom` 种子 → 生成临时密钥 → AES 解密真密钥 → 解密数据 → zlib 解压
- `JavaRandom` 参数：乘子 `0x5DEECE66D`、加数 `0xB`、掩码 `2^48 - 1`

### V3 协议

V3 是当前最复杂的协议，按 `format` 字段分三大族：

| `format` | 族 | 主体读取顺序 | 关键差异 |
|----------|-----|-------------|----------|
| `< 4` | 旧式 V1 族 | Model → Animation → Texture → 后置映射表 | 无控制器、无声音、纹理以 RGBA 块存储 |
| `4 ~ 15` | 过渡族 | Model → Animation → [Controller] → Texture(+subTexture) → Sound → 映射表 → YSMJson | `format > 9` 时新增控制器、声音、`blend_weight`、声音特效 |
| `> 15` | 现代族 | Sound → Function → Language → SubEntity → Animation → Controller → Texture → Model → YSMJson | 资源自带 `sha256`/`hash`，`format ≥ 26` 时子实体拆为 Vehicle/Projectile |

关键阈值：
- `> 9`：动画 `blend_weight`、声音特效、控制器区
- `≥ 15`：`all_cutout`、`disable_preview_rotation`
- `> 15`：现代主顺序、`gui_*` 属性
- `≥ 26`：Vehicle/Projectile 拆分
- `≥ 32`：`merge_multiline_expr`

## V3 解密管线

```
.ysm 文件
  │
  ├─ 1. 解析 YSGP 明文头部 → 提取 format、资源清单 (Source SHA-256 映射)
  │
  ├─ 2. 文件完整性校验
  │      CityHash64WithSeed(文件除尾8字节, SEED_FILE_VERIFICATION) == 尾部hash
  │
  ├─ 3. 变体 XChaCha20 解密
  │      种子: CityHash64WithSeed(key||iv, SEED_RES_VERIFICATION)
  │      块大小: ((hash & 0x3F) | 0x40) << 6
  │      轮数:   10 * (hash % 3) + 10  (= 10 / 20 / 30)
  │      每块解密后: 重新 CityHash → 更新 ChaCha 上下文 (轮数 + input[4..15] XOR hash)
  │
  ├─ 4. MT19937_64 XOR
  │      种子: CityHash64WithSeed(key||iv, SEED_KEY_DERIVATION)
  │      以 64 位小端流密钥逐字节异或
  │
  ├─ 5. Nonce 剥离
  │      前 2 字节 = nonce 长度 (mask 0x3FF)
  │      跳过 2 + nonce_length 字节 → 得到 Zstd 载荷
  │
  ├─ 6. Zstd 解压
  │      流式 ZSTD_decompressStream
  │      得到 decompressed.bin
  │
  └─ 7. 对象反序列化 (按 format 分支)
        读取 uint32 版本号校验 → deserializeLegacyV1 / deserializeLegacyV15 / deserializeModern
```

### 种子常量

| 常量名 | 值 | 用途 |
|--------|-----|------|
| `SEED_KEY_DERIVATION` | `0xD017CBBA7B5D3581` | MT19937 种子派生 |
| `SEED_RES_VERIFICATION` | `0xA62B1A2C43842BC3` | XChaCha20 状态初始化 |
| `SEED_CACHE_DECRYPTION` | `0xD1C3D1D13A99752B` | 服务端缓存解密 |
| `SEED_FILE_VERIFICATION` | `0x9E5599DB80C67C29` | 文件完整性校验 |
| `SEED_PACKET_VERIFICATION` | `0xEE6FA63D570BD77B` | 网络包校验 |
| `SEED_CACHE_VERIFICATION` | `0xF346451E53A22261` | 缓存完整性校验 |

## 输出结构

### 现代协议 (format > 15)

```
<output_dir>/
├── ysm.json                     # 项目元数据 (metadata + properties + files)
├── models/
│   ├── main.json                # 玩家主体模型
│   └── arm.json                 # 手臂模型
├── animations/
│   ├── <name>.animation.json    # 玩家动画
│   ├── vehicle/
│   │   └── <name>.animation.json
│   └── projectile/
│       └── <name>.animation.json
├── controller/
│   └── <name>.json              # 动画控制器
├── textures/
│   └── <name>.png               # 纹理
├── sounds/
│   └── <name>.ogg               # 音频
├── functions/
│   └── <name>.molang            # MoLang 函数
├── lang/
│   └── <name>.json              # 语言文件
├── avatar/
│   └── <name>.png               # 头像
└── background/                   # GUI 背景 (可选)
```

### 旧式协议 (format ≤ 15)

```
<output_dir>/
├── info.json                     # 简化项目信息
├── <model_name>.json            # 模型 (根目录平铺)
├── <anim_name>.animation.json
├── <texture_name>.png
└── ...
```

路径优先由 YSGP 头部的 `Source SHA-256` 清单决定；若头部无映射则回退到上述默认路径。

## 几何反推

当模型数据仅保存顶点、法线与 UV 坐标（无显式 `origin`/`size`/`pivot`/`rotation`/`uv` 框），`restore_blockbench_cube()` 执行以下数学重建：

1. **候选轴提取**：从每个面的法线及边界切线（仅取面片边缘，排除对角线）中提取候选局部坐标轴
2. **旋转搜索**：对候选轴做排列与符号枚举（6 种排列 × 8 种符号 = 48 种组合），Gram-Schmidt 正交化后按 UV 方向匹配度评分，选最优旋转矩阵
3. **包围盒计算**：将世界顶点投影到局部坐标系中，求 min/max 得到 `size`，中心点得到 `pivot`
4. **符号检测**：按面法线方向判定 `size` 的正负符号
5. **UV 还原**：从各面顶点 UV 坐标推导 `uv` 与 `uv_size`，并根据 T_u/T_v 与期望 UV 方向的点积判定是否需要翻转
6. **坐标转换**：考虑 YSM 与 BlockBench 的坐标系差异（X 取反、旋转 X/Y 取反），转换为 `minecraft:geometry` 格式

输出模型使用 `"format_version":"1.12.0"` 的 `minecraft:geometry` 结构，可直接被 BlockBench 导入。

## 常见问题 (Q&A)

**Q：为什么还原出的模型参数（如长宽高、旋转角、中心点）与我的未加密原文件不同？**

**A：** 因为 YSM 导出会将模型 **“烘焙”** 为最底层的顶点和面数据，丢失了原本直观的尺寸参数。为了让模型能在 BlockBench 中再次编辑，YSMParser 使用了**几何反推**算法，通过点和面重新“猜”出模型的参数。由于多种不同的参数组合能拼凑出完全相同的外观，因此反推参数可能与原文件有出入，但请放心，**模型在实际渲染时的视觉外观是完全一致的**。

**Q：为什么我还原出来的模型完全错位，甚至方块扭曲崩塌了？**

**A：** 这通常是因为**几何反推算法出现了误判**。如果原模型包含极其复杂的嵌套旋转、极近的重合顶点或非标准几何体，算法可能会算错特定骨骼的中心点（Pivot）或旋转方向，从而导致关联部件错位或崩塌。如果您遇到了这种情况，欢迎提交 Issue 并附带出错的 `.ysm` 文件。如果条件允许，请尽量附带该模型的未加密原文件，这会对我们优化算法、修复问题提供极大的帮助！

## 依赖

所有依赖以源码形式 vendored 在 `external/` 下，通过 CMake `add_subdirectory` 集成：

| 依赖 | 用途 |
|------|------|
| `zstd` | Zstandard 解压 (V3) |
| `zlib` | zlib 解压 (V1/V2) |
| `cityhash` | CityHash64 哈希 (V3 完整性校验、密钥派生、块状态更新) |
| `xchacha20` | XChaCha20 流密码 (V3 加解密) |
| `AES` | AES-128-CBC (V1/V2) |
| `md5` | MD5 哈希 (V2 密钥派生) |
| `cpp-base64` | Base64 编解码 (V1/V2 文件名) |
| `fpng` | 快速 PNG 编码 (RGBA 纹理转换) |
| `json` | nlohmann JSON 库 (输出 JSON 序列化) |
| `CLI11` | 命令行参数解析 |

## 许可证

[LICENSE.txt](LICENSE.txt)

