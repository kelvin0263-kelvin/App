import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'dart:io';

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

  @override
  Widget build(BuildContext context) {
    return Scaffold(
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
                  style: TextStyle(color: Colors.red),
                ),
              ),
            const SizedBox(height: 20),
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
          ],
        ),
      ),
    );
  }
}