# ExtClipboardManager

[![Build](https://github.com/powerAn2020/ExtClipboardManager/actions/workflows/core.yml/badge.svg)](https://github.com/powerAn2020/ExtClipboardManager/actions/workflows/core.yml)
[![GitHub all releases](https://img.shields.io/github/downloads/powerAn2020/ExtClipboardManager/total?label=Downloads)](https://github.com/powerAn2020/ExtClipboardManager/releases)
[![License](https://img.shields.io/github/license/powerAn2020/ExtClipboardManager)](LICENSE)

一个增强的 Android 剪切板管理器，基于 Xposed 框架，提供剪切板访问控制和自动清除功能。

## 功能特性

### 🕐 超时自动清除
- 设置超时时间（秒），剪切板内容在指定时间后自动清除
- 支持启用/禁用开关

### 📊 读取次数限制
- 设置剪切板最大读取次数，达到次数后自动清除
- 适用于防止应用反复读取剪切板内容

### 🔧 应用过滤（白名单/黑名单）
- **白名单模式**：白名单内的应用读取剪切板时，不计入读取次数
- **黑名单模式**：仅黑名单内的应用读取时才计入读取次数
- 推荐将输入法加入白名单，避免读取次数被输入法消耗

### 📝 内容排除
- 支持纯文本匹配和正则表达式
- 匹配排除规则的剪切板内容不会被自动清除

### 📋 剪切板测试页面
- 实时显示 Hook 状态
- 写入测试文本到剪切板
- 读取剪切板内容
- 重置读取计数器
- 查看读取日志（包名、时间、次数）

### ⚙️ 快捷设置
- 支持 Android 快捷设置磁贴（Quick Settings Tile）
- 一键启用/禁用模块

## 工作原理

通过 Xposed 框架 Hook 系统 `ClipboardService`，在 `getPrimaryClip` 和 `setPrimaryClip` 方法执行前后进行拦截，实现剪切板内容的监控和管理。

## 安装要求

- Android 16+ (HyperOS 3)
- 已安装 Xposed 框架（如 LSPosed）

## 安装步骤

1. 下载最新的 APK 文件
2. 安装 APK
3. 在 LSPosed 中启用模块
4. **勾选"系统框架"作用域**
5. 重启手机或重启 SystemUI
6. 打开 ExtClipboardManager 应用配置功能

## 使用说明

### 启用模块
1. 打开 ExtClipboardManager
2. 启用"剪切板管理"开关
3. 确认服务状态显示"Available"

### 配置自动清除
1. 启用"自动清除"功能
2. 进入"自动清除策略"
3. 选择工作模式（白名单/黑名单）
4. 设置读取次数限制
5. 配置超时清除时间（可选）
6. 添加需要排除的应用或内容

### 使用测试页面
1. 进入"剪切板测试"页面
2. 点击"写入测试文本"验证写入功能
3. 点击"读取剪切板"验证读取功能
4. 查看读取日志确认 Hook 是否正常工作

## 注意事项

- ⚠️ 首次使用记得在 LSPosed 中勾选"系统框架"
- ⚠️ 输入法可能会在剪切后多次读取剪切板，建议将输入法加入白名单
- ⚠️ 部分情况下应用可能通过其他方式读取剪切板，不会触发 `getPrimaryClip`
- ⚠️ 修改配置后可能需要重启应用才能生效

## 致谢

本项目基于 [hhvvg/ExtClipboardManager](https://github.com/hhvvg/ExtClipboardManager) 进行开发，在此感谢原作者 **hhvvg** 的优秀工作。

原作者的贡献包括：
- 基础的 Xposed 模块架构
- ClipboardService Hook 核心逻辑
- 超时自动清除功能
- 读取次数限制功能
- 应用白名单/黑名单过滤
- 内容排除功能
- 快捷设置磁贴支持

在此基础上，本项目新增了：
- 剪切板测试页面与读取日志
- Android 16 / HyperOS 3 适配

## 开发

### 环境要求
- Android Studio Hedgehog+
- JDK 17
- Android SDK 35

### 构建
```bash
./gradlew assembleDebug
```

## License

```
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
