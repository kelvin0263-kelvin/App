import 'dart:typed_data';
import 'package:flutter/services.dart';
import 'package:image/image.dart' as img;
import 'dart:math';

class ImageDetector {
  /// Detects if a template image exists within a larger image
  /// Returns the position and confidence score if found
  static Future<DetectionResult?> detectTemplate({
    required String templatePath,
    required String sourcePath,
    double threshold = 0.1,
  }) async {
    try {
      print('Loading images...');
      // Load both images
      final templateBytes = await rootBundle.load(templatePath);
      final sourceBytes = await rootBundle.load(sourcePath);

      print('Decoding images...');
      // Decode images with error handling
      img.Image? template = img.decodeImage(templateBytes.buffer.asUint8List());
      img.Image? source = img.decodeImage(sourceBytes.buffer.asUint8List());

      if (template == null || source == null) {
        print('Failed to decode images');
        return null;
      }

      print('Template size: ${template.width}x${template.height}');
      print('Source size: ${source.width}x${source.height}');

      // Resize images if they're too large to improve performance
      const maxDimension = 400; // Smaller max dimension for better performance
      
      // First resize the source image
      if (source.width > maxDimension || source.height > maxDimension) {
        final scale = maxDimension / max(source.width, source.height);
        source = img.copyResize(
          source,
          width: (source.width * scale).round(),
          height: (source.height * scale).round(),
        );
        print('Resized source to: ${source.width}x${source.height}');
      }

      // Then resize the template to be smaller than the source
      final templateScale = min(
        source.width / template.width,
        source.height / template.height
      ) * 0.8; // Make template 80% of source size

      template = img.copyResize(
        template,
        width: (template.width * templateScale).round(),
        height: (template.height * templateScale).round(),
      );
      print('Resized template to: ${template.width}x${template.height}');

      print('Starting template matching...');
      
      // Use larger step size for better performance
      const stepSize = 4; // Check every 4th pixel
      double minSsd = double.infinity;
      Point<int>? bestPosition;
      int totalSteps = ((source.height - template.height) ~/ stepSize) * 
                      ((source.width - template.width) ~/ stepSize);
      int currentStep = 0;

      for (int y = 0; y <= source.height - template.height; y += stepSize) {
        for (int x = 0; x <= source.width - template.width; x += stepSize) {
          currentStep++;
          if (currentStep % 100 == 0) {
            print('Progress: ${(currentStep * 100 / totalSteps).toStringAsFixed(1)}%');
          }

          double currentSsd = 0;
          int pixelCount = 0;

          // Only check every 4th pixel in template for better performance
          for (int ty = 0; ty < template.height; ty += 4) {
            for (int tx = 0; tx < template.width; tx += 4) {
              final pixelSource = source.getPixel(x + tx, y + ty);
              final pixelTemplate = template.getPixel(tx, ty);

              final dr = pixelSource.r - pixelTemplate.r;
              final dg = pixelSource.g - pixelTemplate.g;
              final db = pixelSource.b - pixelTemplate.b;
              
              currentSsd += (dr * dr) + (dg * dg) + (db * db);
              pixelCount++;
            }
          }

          // Calculate average SSD
          currentSsd /= pixelCount;

          if (currentSsd < minSsd) {
            minSsd = currentSsd;
            bestPosition = Point(x, y);
            print('Found better match: SSD=${minSsd.toStringAsFixed(2)} at ($x, $y)');
          }
        }
      }

      // Convert SSD to confidence score (lower SSD = higher confidence)
      const maxSsd = 100000.0; // Increased from 50000.0 to allow for more variation
      final confidence = 1.0 - (minSsd / maxSsd).clamp(0.0, 1.0);

      print('Best confidence found: ${(confidence * 100).toStringAsFixed(2)}%');
      print('Threshold: ${(threshold * 100).toStringAsFixed(2)}%');
      print('Raw SSD value: ${minSsd.toStringAsFixed(2)}');

      if (confidence >= threshold && bestPosition != null) {
        print('Match found at position: (${bestPosition.x}, ${bestPosition.y})');
        return DetectionResult(
          location: bestPosition,
          confidence: confidence,
          templateSize: Size(template.width.toDouble(), template.height.toDouble()),
        );
      } else {
        print('No match found - confidence too low or no position found');
        print('Best position: ${bestPosition != null ? "(${bestPosition.x}, ${bestPosition.y})" : "null"}');
      }

      return null;
    } catch (e) {
      print('Error in image detection: $e');
      return null;
    }
  }
}

class DetectionResult {
  final Point<int> location;
  final double confidence;
  final Size templateSize;

  DetectionResult({
    required this.location,
    required this.confidence,
    required this.templateSize,
  });

  /// Returns the center point of the detected template
  Point<int> get center => Point(
    location.x + (templateSize.width ~/ 2),
    location.y + (templateSize.height ~/ 2),
  );
} 