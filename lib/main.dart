import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:io';
import 'dart:ui' as ui;
import 'dart:typed_data';
import 'package:flutter/rendering.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Game Script',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Game Script'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool _hasOverlayPermission = false;
  bool _isFloatingWindowActive = false;
  String? _errorMessage;
  static const platform = MethodChannel('com.example.my_game_script/overlay');
  
  // Add screenshot related variables
  final GlobalKey _repaintBoundaryKey = GlobalKey();
  Uint8List? _screenshotBytes;
  bool _isCapturing = false;

  @override
  void initState() {
    super.initState();
    _setupMethodChannel();
    _checkOverlayPermission();
  }

  void _setupMethodChannel() {
    platform.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'onOverlayPermissionResult':
          setState(() {
            _hasOverlayPermission = call.arguments as bool;
            _errorMessage = null;
          });
          break;
        case 'onOverlayPermissionError':
          setState(() {
            _errorMessage = call.arguments as String?;
          });
          break;
        case 'onScreenshot':
          print("DEBUG_BOT: Screenshot received on Dart side!");
          // When a screenshot arrives, update the state
          final bytes = call.arguments as Uint8List;
          print("DEBUG_BOT: Screenshot size: ${bytes.length} bytes");
          setState(() {
            _screenshotBytes = bytes;
          });
          print("DEBUG_BOT: Updated UI with screenshot");
          break;
      }
    });
  }

  Future<void> _checkOverlayPermission() async {
    try {
      final bool hasPermission = await platform.invokeMethod('checkOverlayPermission');
      setState(() {
        _hasOverlayPermission = hasPermission;
        _errorMessage = null;
      });
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to check overlay permission: $e';
      });
    }
  }

  Future<void> _requestOverlayPermission() async {
    try {
      await platform.invokeMethod('requestOverlayPermission');
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to request overlay permission: $e';
      });
    }
  }

  Future<void> _startFloatingWindow() async {
    try {
      final bool success = await platform.invokeMethod('startFloatingWindow');
      if (success) {
        setState(() {
          _isFloatingWindowActive = true;
          _errorMessage = null;
        });
      } else {
        setState(() {
          _errorMessage = 'Failed to start floating window';
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to start floating window: $e';
      });
    }
  }

  Future<void> _stopFloatingWindow() async {
    try {
      final bool success = await platform.invokeMethod('stopFloatingWindow');
      if (success) {
        setState(() {
          _isFloatingWindowActive = false;
          _errorMessage = null;
        });
      } else {
        setState(() {
          _errorMessage = 'Failed to stop floating window';
        });
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to stop floating window: $e';
      });
    }
  }

  // Add screenshot capture function
  Future<void> _captureScreenshot() async {
    try {
      setState(() {
        _isCapturing = true;
      });

      // Find the RenderObject from the GlobalKey
      RenderRepaintBoundary boundary = _repaintBoundaryKey.currentContext!.findRenderObject() as RenderRepaintBoundary;
      
      // Capture the boundary as an image
      ui.Image image = await boundary.toImage(pixelRatio: 3.0);
      
      // Convert the image to byte data in PNG format
      ByteData? byteData = await image.toByteData(format: ui.ImageByteFormat.png);
      
      if (byteData != null) {
        setState(() {
          _screenshotBytes = byteData.buffer.asUint8List();
          _isCapturing = false;
        });
        print("Screenshot captured successfully!");
      }
    } catch (e) {
      print("Error capturing screenshot: $e");
      setState(() {
        _isCapturing = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return RepaintBoundary(
      key: _repaintBoundaryKey,
      child: Scaffold(
        appBar: AppBar(
          backgroundColor: Theme.of(context).colorScheme.inversePrimary,
          title: Text(widget.title),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: <Widget>[
              Text(
                'Overlay Permission: ${_hasOverlayPermission ? "Granted" : "Not Granted"}',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              Text(
                'Floating Window: ${_isFloatingWindowActive ? "Active" : "Inactive"}',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              if (_errorMessage != null)
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Text(
                    _errorMessage!,
                    style: const TextStyle(color: Colors.red),
                  ),
                ),
              const SizedBox(height: 20),
              if (_screenshotBytes != null)
                Padding(
                  padding: const EdgeInsets.all(8.0),
                  child: Image.memory(
                    _screenshotBytes!,
                    width: 300,
                    height: 200,
                    fit: BoxFit.contain,
                  ),
                ),
              if (!_hasOverlayPermission)
                ElevatedButton(
                  onPressed: _requestOverlayPermission,
                  child: const Text('Grant Overlay Permission'),
                ),
              if (_hasOverlayPermission && !_isFloatingWindowActive)
                ElevatedButton(
                  onPressed: _startFloatingWindow,
                  child: const Text('Start Floating Window'),
                ),
              if (_isFloatingWindowActive)
                ElevatedButton(
                  onPressed: _stopFloatingWindow,
                  child: const Text('Stop Floating Window'),
                ),
              const SizedBox(height: 20),
              // Add screenshot button
              ElevatedButton(
                onPressed: _isCapturing ? null : _captureScreenshot,
                child: Text(_isCapturing ? 'Capturing...' : 'Take Screenshot'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}