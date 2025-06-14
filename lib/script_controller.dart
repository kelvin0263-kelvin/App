// lib/script_controller.dart

import 'dart:async';
import 'dart:typed_data';
import 'package:flutter/services.dart' show rootBundle;
import 'package:image/image.dart' as img;
import 'template_matcher.dart'; // 导入我们上面的图像匹配文件
import 'dart:math';
import 'dart:io';
import 'package:flutter/services.dart';

// 这是一个【模拟】的API，用于本地测试，它从assets加载图片而不是真的截图和点击
class MockNativeApi {
  static Future<Uint8List?> takeScreenshot() async {
      ("模拟：从 assets 加载 'img1.jpg'...");
    // 我们用回你最开始的文件名，你也可以改成 sample_screenshot.jpg
    return (await rootBundle.load('assets/img1.jpg')).buffer.asUint8List();
  }
  
  static Future<void> performTap(Point<int> center) async {
    print("✅ 模拟点击成功！目标坐标: (${center.x}, ${center.y})");
  }
}

class ScriptController {
  final String _templatePath;
  final String _screenshotPath;
  static const platform = MethodChannel('com.example.my_game_script/screen_capture');
  bool _isCapturing = false;
  
  ScriptController({
    required String templatePath,
    required String screenshotPath,
  }) : _templatePath = templatePath,
       _screenshotPath = screenshotPath {
    platform.setMethodCallHandler(_handleMethod);
  }

  Future<dynamic> _handleMethod(MethodCall call) async {
    switch (call.method) {
      case 'onScreenshot':
        final bytes = call.arguments as Uint8List;
        // Process the screenshot bytes
        final screenshotImage = img.decodeImage(bytes);
        if (screenshotImage != null) {
          // Load template
          final templateBytes = (await rootBundle.load(_templatePath)).buffer.asUint8List();
          final templateImage = img.decodeImage(templateBytes);
          
          if (templateImage != null) {
            // Find template in screenshot
            final match = await findTemplateInImage(screenshotImage, templateImage);
            if (match != null) {
              print('Found match at: ${match.x}, ${match.y}');
              // TODO: Implement click action
            }
          }
        }
        break;
    }
  }

  /// Starts screen capture
  Future<void> startScreenCapture() async {
    if (!_isCapturing) {
      try {
        await platform.invokeMethod('startScreenCapture');
        _isCapturing = true;
      } catch (e) {
        print('Error starting screen capture: $e');
      }
    }
  }

  /// Stops screen capture
  Future<void> stopScreenCapture() async {
    if (_isCapturing) {
      try {
        await platform.invokeMethod('stopScreenCapture');
        _isCapturing = false;
      } catch (e) {
        print('Error stopping screen capture: $e');
      }
    }
  }

  /// Finds template in a given image
  Future<Point<int>?> findTemplateInImage(img.Image screenshot, img.Image template) async {
    // Resize images if needed
    const maxDimension = 400;
    
    if (screenshot.width > maxDimension || screenshot.height > maxDimension) {
      final scale = maxDimension / max(screenshot.width, screenshot.height);
      screenshot = img.copyResize(
        screenshot,
        width: (screenshot.width * scale).round(),
        height: (screenshot.height * scale).round(),
      );
    }

    final templateScale = min(
      screenshot.width / template.width,
      screenshot.height / template.height
    ) * 0.8;

    template = img.copyResize(
      template,
      width: (template.width * templateScale).round(),
      height: (template.height * templateScale).round(),
    );

    // Template matching
    const stepSize = 4;
    double minSsd = double.infinity;
    Point<int>? bestPosition;

    for (int y = 0; y <= screenshot.height - template.height; y += stepSize) {
      for (int x = 0; x <= screenshot.width - template.width; x += stepSize) {
        double currentSsd = 0;
        int pixelCount = 0;

        for (int ty = 0; ty < template.height; ty += 4) {
          for (int tx = 0; tx < template.width; tx += 4) {
            final pixelScreenshot = screenshot.getPixel(x + tx, y + ty);
            final pixelTemplate = template.getPixel(tx, ty);

            final dr = pixelScreenshot.r - pixelTemplate.r;
            final dg = pixelScreenshot.g - pixelTemplate.g;
            final db = pixelScreenshot.b - pixelTemplate.b;
            
            currentSsd += (dr * dr) + (dg * dg) + (db * db);
            pixelCount++;
          }
        }

        currentSsd /= pixelCount;

        if (currentSsd < minSsd) {
          minSsd = currentSsd;
          bestPosition = Point(x, y);
        }
      }
    }

    const maxSsd = 50000.0;
    final confidence = 1.0 - (minSsd / maxSsd).clamp(0.0, 1.0);

    if (confidence >= 0.1 && bestPosition != null) {
      return bestPosition;
    }

    return null;
  }

  /// Takes a screenshot and saves it to the specified path
  Future<void> takeScreenshot() async {
    // TODO: Implement screenshot functionality
    // This would typically use platform-specific APIs
    print('Taking screenshot...');
  }

  /// Finds a template image within the current screenshot
  Future<Point<int>?> findTemplate() async {
    try {
      final templateFile = File(_templatePath);
      final screenshotFile = File(_screenshotPath);
      
      if (!templateFile.existsSync() || !screenshotFile.existsSync()) {
        print('Template or screenshot file not found');
        return null;
      }

      return TemplateMatcher.findTemplate(screenshotFile, templateFile);
    } catch (e) {
      print('Error finding template: $e');
      return null;
    }
  }

  /// Simulates a mouse click at the specified coordinates
  Future<void> clickAt(Point<int> position) async {
    // TODO: Implement mouse click simulation
    print('Clicking at position: ${position.x}, ${position.y}');
  }

  /// Runs the automation script
  Future<void> runScript() async {
    try {
      await takeScreenshot();
      final match = await findTemplate();
      
      if (match != null) {
        await clickAt(match);
      } else {
        print('Template not found in screenshot');
      }
    } catch (e) {
      print('Error running script: $e');
    }
  }

  // 这是我们的测试主方法
  void runTestWithYourImages() async {
    try {
      print("--- 开始使用你的图片进行测试 ---");

      // 1. 加载你的模板图片 (go.jpg)
      print("加载模板 'go.jpg'...");
      final templateBytes = (await rootBundle.load('assets/go.jpg')).buffer.asUint8List();
      var templateImage = img.decodeImage(templateBytes);

      if (templateImage == null) {
        print("错误：无法解码模板图片。");
        return;
      }

      print("原始模板图片尺寸: ${templateImage.width}x${templateImage.height}");

      // 2. 加载你的屏幕截图 (img1.jpg)
      print("加载截图 'img1.jpg'...");
      final screenshotBytes = await MockNativeApi.takeScreenshot();
      if (screenshotBytes == null) {
        print("错误：无法加载截图。");
        return;
      }

      var screenshotImage = img.decodeImage(screenshotBytes);
      if (screenshotImage == null) {
        print("错误：无法解码截图。");
        return;
      }

      print("原始截图尺寸: ${screenshotImage.width}x${screenshotImage.height}");

      // 3. 调整图片大小以提高性能
      const maxDimension = 400; // 更小的最大尺寸
      
      // 首先调整截图
      if (screenshotImage.width > maxDimension || screenshotImage.height > maxDimension) {
        final scale = maxDimension / max(screenshotImage.width, screenshotImage.height);
        screenshotImage = img.copyResize(
          screenshotImage,
          width: (screenshotImage.width * scale).round(),
          height: (screenshotImage.height * scale).round(),
        );
        print("调整后的截图尺寸: ${screenshotImage.width}x${screenshotImage.height}");
      }

      // 然后调整模板，确保它比截图小
      final templateScale = min(
        screenshotImage.width / templateImage.width,
        screenshotImage.height / templateImage.height
      ) * 0.8; // 确保模板比截图小20%

      templateImage = img.copyResize(
        templateImage,
        width: (templateImage.width * templateScale).round(),
        height: (templateImage.height * templateScale).round(),
      );
      print("调整后的模板尺寸: ${templateImage.width}x${templateImage.height}");

      // 4. 核心：调用图像匹配算法
      print("正在分析图像，请稍候...");
      
      // 使用更大的搜索步长来提高性能
      const stepSize = 4; // 每4个像素检查一次
      double minSsd = double.infinity;
      Point<int>? bestPosition;
      int totalSteps = ((screenshotImage.height - templateImage.height) ~/ stepSize) * 
                      ((screenshotImage.width - templateImage.width) ~/ stepSize);
      int currentStep = 0;

      for (int y = 0; y <= screenshotImage.height - templateImage.height; y += stepSize) {
        for (int x = 0; x <= screenshotImage.width - templateImage.width; x += stepSize) {
          currentStep++;
          if (currentStep % 100 == 0) {
            print("进度: ${(currentStep * 100 / totalSteps).toStringAsFixed(1)}%");
          }

          double currentSsd = 0;
          int pixelCount = 0;

          // 只检查模板中的部分像素以提高性能
          for (int ty = 0; ty < templateImage.height; ty += 4) {
            for (int tx = 0; tx < templateImage.width; tx += 4) {
              final pixelScreenshot = screenshotImage.getPixel(x + tx, y + ty);
              final pixelTemplate = templateImage.getPixel(tx, ty);

              final dr = pixelScreenshot.r - pixelTemplate.r;
              final dg = pixelScreenshot.g - pixelTemplate.g;
              final db = pixelScreenshot.b - pixelTemplate.b;
              
              currentSsd += (dr * dr) + (dg * dg) + (db * db);
              pixelCount++;
            }
          }

          // 计算平均SSD
          currentSsd /= pixelCount;

          if (currentSsd < minSsd) {
            minSsd = currentSsd;
            bestPosition = Point(x, y);
            print("找到更好的匹配: SSD=${minSsd.toStringAsFixed(2)} 位置=(${x}, ${y})");
          }
        }
      }

      // 5. 分析匹配结果
      if (bestPosition != null) {
        print("🎉 找到最佳匹配！");
        print("   - 左上角坐标: (${bestPosition.x}, ${bestPosition.y})");
        print("   - 相似度 (SSD值): ${minSsd.toStringAsFixed(2)}");

        // 设置一个阈值来判断是否是有效匹配
        const double ssdThreshold = 50000.0; // 增加阈值以适应更宽松的匹配

        if (minSsd < ssdThreshold) {
          print("匹配度高于阈值，确认为目标！");
          // 计算模板的中心点坐标用于点击
          final int centerX = bestPosition.x + (templateImage.width ~/ 2);
          final int centerY = bestPosition.y + (templateImage.height ~/ 2);
          
          // 6. 模拟点击
          await MockNativeApi.performTap(Point(centerX, centerY));
        } else {
          print("匹配度低于阈值，忽略。");
        }
      } else {
        print("❌ 未找到任何匹配项。");
      }
    } catch (e, stackTrace) {
      print("❌ 发生错误: $e");
      print("堆栈跟踪: $stackTrace");
    }
    print("--- 测试结束 ---");
  }
}