# Cycle Stunt Game

A simple physics-based cycle stunt game built in Godot 4.

## Controls

- **D / Right Arrow**: Accelerate
- **S**: Brake  
- **A**: Tilt Left
- **D**: Tilt Right
- **R**: Restart Level

## Game Mechanics

- Control a bike with realistic physics
- Navigate ramps and obstacles
- Avoid crashing (frame touching ground)
- Complete levels by reaching the end

## Project Structure

```
cycle_stunt_game/
├── scenes/
│   ├── bike.tscn          # Bike physics setup
│   ├── level_1.tscn       # First level
│   └── main.tscn          # Main scene
├── scripts/
│   ├── bike_frame.gd      # Bike tilt control
│   ├── wheel.gd           # Wheel acceleration
│   └── game_manager.gd    # Game logic
└── project.godot          # Godot project settings
```

## How to Play

1. Open in Godot 4.x
2. Press F5 to run
3. Use controls to navigate the bike
4. Try to complete the level without crashing!

## Next Steps

- Add more levels
- Implement crash detection properly
- Add mobile touch controls
- Include sound effects
- Add score system
