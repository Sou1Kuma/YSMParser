<div align="center">
  <img src="images/brand.jpg" alt="logo" width="300"/>
  <h1>YSMParser</h1>
  <p>.ysm模型一键解密、转换为Blockbench工程，全版本加密格式支持。</p>
</div>

https://github.com/user-attachments/assets/e3a3ca69-7d1f-4639-a925-4980ddf03e26

## 总览

[是，史蒂夫模型](https://github.com/YesSteveModel)是一款[《我的世界》](https://www.minecraft.net/zh-hant)模型载入模组。由于作者[TartaricAcid](https://github.com/TartaricAcid)拟似患上[偏执型人格障碍](https://zh.wikipedia.org/wiki/%E5%81%8F%E5%9F%B7%E5%9E%8B%E4%BA%BA%E6%A0%BC%E9%9A%9C%E7%A4%99)，在原有的不安全加密方案出现公共攻击之后为了保护他的兒子，使用C++重写了加密和渲染，且渲染使用C++编写完全为了防止Java Hooks导出模型。为了防止社区认为他是精神障碍患者，其在[Modrinth主页](https://modrinth.com/mod/yes-steve-model)上声称使用被盗的VMProtect和没有缓存的jni渲染是极大的提升了渲染效率。

本项目提供了完全解密其C++重写版本的加密及所有旧版本加密的方案。

![1](images/platform.png)

## 功能

- 支援全部格式版本
- 当模型仅保存顶点/法线/UV 时，自动还原BlockBench的 origin、size、pivot、rotation与各面uv字段
- 还原工程文件： `models`、`animations`、`controller`、`textures`、`sounds`、`lang`、`avatar`、`ysm.json`

### 格式支持

| 格式 | 支援 |
|------|------|
| V1 | ✅ |
| V2 | ✅ |
| V3 (`format` < 4) | ✅ |
| V3 (`format` 4 ~ 15) | ✅ |
| V3 (`format` > 15) | ✅ |
| V3 (`format` ≥ 26) | ✅ |
| V3 (`format` ≥ 32) | ✅ |

## 教学

1. 构建或者[下载](https://github.com/OpenYSM/YSMParser/releases)符合您的作业系统的可执行文件
2. 创建一个输入目录，将所有需要解密的.ysm文件放入
3. 运行命令列

```bash
YSMParser -i <输入目录> -o <输出目录> [-v] [-j <线程数>]
```

| 参数 | 说明 |
|------|------|
| `-i, --input` | 输入目录（必须），将处理其中的全部 `.ysm` 文件 |
| `-o, --output` | 输出根目录（必须） |
| `-v, --verbose` | 详细输出模式（强制单线程） |
| `-j, --threads` | 并行线程数（默认 `0` = 自动检测 CPU 核心数） |
| `--version` | 显示版本号 |

示例：

```bash
# 所有文件放入input文件夹
YSMParser -i input -o output
```

## 构建

环境：
- CMake ≥ 3.12
- C++20 Compiler

### 可执行文件构建

```bash
# 配置 (Windows x64 Release)
cmake --preset x64-release

# 编译
cmake --build --preset x64-release
```

### WASM构建

```bash
# Node.js
cmake --preset wasm-release
cmake --build --preset wasm-release

# Web
cmake --preset wasm-web-release
cmake --build --preset wasm-web-release
```

WASM构建需要EMSDK环境变量指向Emscripten安装目录。

## FAQ

**Q：为什么还原出的模型参数（如长宽高、旋转角、中心点）与我的未加密原文件不同？**

**A：** 因为 YSM 导出会将模型 **“烘焙”** 为最底层的顶点和面数据，丢失了原本直观的尺寸参数。为了让模型能在 BlockBench 中再次编辑，YSMParser 使用了**几何反推**算法，通过点和面重新“猜”出模型的参数。由于多种不同的参数组合能拼凑出完全相同的外观，因此反推参数可能与原文件有出入，但请放心，**模型在实际渲染时的视觉外观是完全一致的**。

**Q：为什么我还原出来的模型完全错位，甚至方块扭曲崩塌了？**

**A：** 这通常是因为**几何反推算法出现了误判**。如果原模型包含极其复杂的嵌套旋转、极近的重合顶点或非标准几何体，算法可能会算错特定骨骼的中心点（Pivot）或旋转方向，从而导致关联部件错位或崩塌。如果您遇到了这种情况，欢迎提交 Issue 并附带出错的 `.ysm` 文件。如果条件允许，请尽量附带该模型的未加密原文件，这会对我们优化算法、修复问题提供极大的帮助！

## 鸣谢

- [TartaricAcid](https://github.com/TartaricAcid): 下载被盗的VMProtect并misconfiguration导致所有关键函数没有被加密并随机按压数字小键盘为[ChatGPT 4o(Free tier)](https://chatgpt.com/)推荐的加密算术输入新的魔数
- [Ivan Permyakov](https://vmpsoft.com/): 创建VMProtect
- [Geoff Pike](https://github.com/google/cityhash): 创建CityHash64
- Makoto Matsumoto: 创建MT19937_64
- Scott Arciszewski: 创建XChaCha20
- [ChatGPT 4o](https://openai.com/zh-Hant/index/hello-gpt-4o/): 帮助[TartaricAcid](https://github.com/TartaricAcid)创建加密方案


### 依赖库

所有依赖以源码形式vendored在`external`下。

| 依赖 |
|------|
| `zstd` |
| `zlib` |
| `cityhash` |
| `xchacha20` |
| `AES` |
| `md5` |
| `cpp-base64` |
| `fpng` |
| `json` |
| `CLI11` |
