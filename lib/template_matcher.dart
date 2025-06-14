// lib/template_matcher.dart

import 'dart:typed_data';
import 'package:image/image.dart' as img;
import 'dart:math';
import 'dart:io';

// 一个简单的类来存储匹配结果
class MatchResult {
  final Point<int> location; // 匹配到的左上角坐标
  final double similarity;   // 相似度分数 (SSD值，越小越像)

  MatchResult(this.location, this.similarity);
}

/// 在大图中寻找小图模板的最佳匹配位置
/// [screenshotBytes] 是屏幕截图的字节数据
/// [templateBytes] 是模板图片的字节数据
MatchResult? findBestMatch(Uint8List screenshotBytes, Uint8List templateBytes) {
  // 1. 将字节数据解码成图片对象
  final img.Image? screenshot = img.decodeImage(screenshotBytes);
  final img.Image? template = img.decodeImage(templateBytes);

  // 确保图片成功解码
  if (screenshot == null || template == null) {
    print("错误：无法解码图片。");
    return null;
  }
  
  // 确保模板尺寸不大于截图
  if (template.width > screenshot.width || template.height > screenshot.height) {
    print("错误：模板比截图大。");
    return null;
  }

  // 2. 初始化用于记录最佳匹配结果的变量
  double minSsd = double.infinity; // 最小差平方和，初始设为无穷大
  Point<int> bestPosition = const Point(0, 0);

  // 3. 遍历大图（截图）中所有可能的左上角起点
  for (int y = 0; y <= screenshot.height - template.height; y++) {
    for (int x = 0; x <= screenshot.width - template.width; x++) {
      
      double currentSsd = 0;
      // 4. 对于每一个可能的起点，计算其与模板的差平方和(SSD)
      for (int ty = 0; ty < template.height; ty++) {
        for (int tx = 0; tx < template.width; tx++) {
          // 获取截图和模板上对应位置的像素
          final pixelScreenshot = screenshot.getPixel(x + tx, y + ty);
          final pixelTemplate = template.getPixel(tx, ty);

          // 计算 R, G, B 通道的差值的平方，并累加
          final dr = pixelScreenshot.r - pixelTemplate.r;
          final dg = pixelScreenshot.g - pixelTemplate.g;
          final db = pixelScreenshot.b - pixelTemplate.b;
          
          currentSsd += (dr * dr) + (dg * dg) + (db * db);
        }
      }
      
      // 5. 如果找到了一个差值更小的位置（即更相似），就更新最佳匹配记录
      if (currentSsd < minSsd) {
        minSsd = currentSsd;
        bestPosition = Point(x, y);
      }
    }
  }

  if (minSsd == double.infinity) return null;

  // 6. 返回最佳匹配的位置和其相似度分数
  return MatchResult(bestPosition, minSsd);
}

class TemplateMatcher {
  /// Matches a template image within a larger image
  /// Returns the position (x, y) of the match if found, null otherwise
  static Point<int>? findTemplate(File sourceImage, File templateImage) {
    try {
      final source = img.decodeImage(sourceImage.readAsBytesSync());
      final template = img.decodeImage(templateImage.readAsBytesSync());
      
      if (source == null || template == null) {
        return null;
      }

      // Simple template matching implementation
      for (int y = 0; y < source.height - template.height; y++) {
        for (int x = 0; x < source.width - template.width; x++) {
          bool match = true;
          
          for (int ty = 0; ty < template.height && match; ty++) {
            for (int tx = 0; tx < template.width && match; tx++) {
              if (source.getPixel(x + tx, y + ty) != template.getPixel(tx, ty)) {
                match = false;
              }
            }
          }
          
          if (match) {
            return Point<int>(x, y);
          }
        }
      }
      
      return null;
    } catch (e) {
      print('Error in template matching: $e');
      return null;
    }
  }
}