# Technical data

The exact numbers, for those who want them. These are the defaults; they can be changed in
the file config/every_tunnel/gameplay.json.

## Stamina

- 100 points.
- Sprinting uses 12 per second; each jump uses 8.
- It comes back at 15 per second, 1.5 seconds after you last used it.
- At 0 you are out of breath until it is back to 30.

## Thirst

- 100 points. It goes down 5 per minute: a full drink lasts about 20 minutes.
- Twice as fast while sprinting.
- Empty: 1 damage (half a heart) every 10 seconds.
- A water bottle gives back 40 and takes 1.5 seconds to drink.
- A working water dispenser gives back 40 each drink, one drink every 1.5 seconds, as often
  as you like.

## Flashlight

- A full battery lasts 3 minutes of light.
- Below 20% the battery row blinks and the light starts to fail.
- A new battery fills it completely and takes 1.5 seconds to put in.
- The beam reaches 20 blocks.

## Noise

How far each noise carries, in blocks, with nothing in the way:

- Sneaking: 2 per step.
- Flashlight click, battery change, drinking: 4.
- Panting: 5, once a second.
- Walking: 6 per step.
- Jumping: 8.
- Doors, buttons, levers, chests: 8.
- Landing: 10 after a 2-block fall, from 5 for small drops up to 20 for long falls.
- Sprinting: 14 per step.

Every solid block in between cuts the distance: one wall to 60%, two walls to 36%.

## The one who listens

- A noise loud enough makes it run at once: for example, sprinting within about 7 blocks
  with no wall in between.
- After a faint noise it stands and listens for 2 seconds.
- After losing you it searches for 4 seconds.
- It gives up walking to a noise after 20 seconds.
- One touch is enough.

## The one who watches

- It sees up to 24 blocks ahead, in a quarter circle round where it looks; 12 if you sneak,
  32 with your flashlight on. Walls and shelves block its view.
- Within 2 blocks it notices you whichever way it looks.
- It walks towards you first; if you stay in sight for 1.5 seconds, or come within 6 blocks,
  it runs.
- It does not hear you at all.
- One touch is enough.

## Your warning signs

- Heartbeat: from 24 blocks away, 60 to 140 beats per minute as it gets closer.
- Its running footsteps are heard up to 24 blocks away, walking up to 16.
- Sometimes a clock ticks in your head: from 24 blocks away, faster and louder as the one
  who watches gets closer.
