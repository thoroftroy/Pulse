# Pulse changelog

## v0.9.0

- Every sensor now has something real to do with it, under its live reading (Device › Sensors, tap one), each with a line on what everyday apps use it for:
  - Accelerometer: a ball-rolling game. Tilt the phone to roll the ball into the hole; it counts holes and your fastest one
  - Movement: a shake-o-meter with your best shake in g
  - Proximity: a wave challenge, as many waves as you can in 10 seconds
  - Magnetic field: a metal detector that finds screws and pipes in a wall, with an optional beep that speeds up as you get closer
  - Gyroscope: a spin counter with your fastest spin
  - Air pressure: how many metres and floors you have climbed since you started
  - Light: whether there is enough light to read where you are
- The spirit level's bubble now floats to the high side when you tilt the top of the phone (it went the wrong way)
- More ? buttons: the settings that were not obvious now explain in a line or two what they do and why you might want them

## v0.8.1

- The moving background holds still while Battery Saver is on, so it uses next to no power then. It still changes with what the app is doing

## v0.8.0

- The background heartbeat quickens and grows with the real processor load, the processor's trace runs beneath it, and the battery shows as a tide that is green when full and red when nearly empty
- The background answers your finger: a tap sends rings out and pops what it lands on, and dragging or scrolling leaves a wake and stirs the scene. Saving, adding, deleting and finishing things send a wave of colour through it, and an error gives a red shudder
- The animated background now has a picture of its own behind everything: a heartbeat line running across a soft grid. It is faint, so text stays easy to read
- Aqua changes far more through the day: a rose dawn, gold sunrise, turquoise morning, deep azure noon, warm afternoon, orange sunset, violet dusk and a real dark night with the moon shimmering through the water and glowing plankton. Now and then a cloud passes over and the light dims
- Super's space changes with the time of day too: a dark night with brighter stars, a rose dawn, teal morning, cobalt noon and a red and amber sunset
- Opening the app no longer sprays a burst of fast symbols across the background; celebrations are a gentle lift of a few symbols, and only for something you just did

## v0.7.0

- The Live background follows your phone: an easy green when it is idle, warmer and faster as the processor works, red and racing under full load or when the battery is hot, a bright charging green with lightning bolts while it charges, and a tired grey when the battery is nearly empty

## v0.6.5

- The symbols drifting in the background are smaller and fainter so they no longer crowd text; selected segments and chips are readable in every look (including Super); the warning banner at the top has a solid backing so its text is easy to read

## v0.6.4

- The animated background is now drawn directly on the screen at full sharpness instead of a small picture stretched up, so it is smooth and no longer looks pixelated; the grain over it is much subtler, and long tab names (like Feedback) shrink to fit instead of being cut off

## v0.6.3

- Cinema is no longer a separate choice in the feedback form: it is part of Memories

## v0.6.2

- At night the animated background gets a stronger wash, so text stays easy to read on dark scenes

## v0.6.1

- Each app's animated scene now has its own drifting symbols (maths signs in Abacus, notes in Echo, arrows in Wayfinder and so on), and the scene is a little clearer while text stays easy to read
- Animated backgrounds reworked after your report: they are drawn at a higher resolution and smoothly at 24 frames a second without slowing the app down, and no longer show horizontal lines

## v0.6.0

- Sensors explain what they measure in plain words and have interactive demos: a spirit level, light meter, hand detector, compass, spinner, magnet finder, altitude guess and step counter
- Every app window now has the animated scene behind it, in the app's own colour (for example orange water in Abacus, green in Pulse, purple in Scribble), following your Aqua, Bliss or Super look and the real sunrise and sunset. Motion Reduced shows a still picture and Off turns it off
- Help buttons (?) explain the more confusing settings

## v0.5.20

- Fixed a gap above the keyboard in screens with a bottom tab bar: the tab bar now steps aside while you type, so you can see what you are writing

## v0.5.19

- The Send report button now sits right under the message box, so there is no scrolling to find it
- After a report is sent the app picker, search and type reset, ready for the next report

## v0.5.18

- Feedback can attach the recent crashes of the apps you pick (on by default, with file paths and links removed), so a crash report arrives with its details

## v0.5.17

- Feedback has a search box for finding an app by name; scrolling the row still works

## v0.5.16

- Feedback now sends straight to the developer with one tap, no GitHub account or browser needed, through the same path crash reports use
- New Public text button copies the report without your device details, with a report ID, for posting publicly

## v0.5.15

- New Feedback tab: pick one app, several or none, choose Problem, Idea or Question, write your message and send it. The report carries the app versions and, if you leave it on, your device details; you see everything before it goes
- Send opens the right project page on GitHub with the report filled in, or copy or share the text

## v0.5.14

- New setting: how many readings the graphs keep

## v0.5.13

- New setting: keep the screen on while watching readings

## v0.5.12

- New settings: how often the live readings update, and Fahrenheit for temperatures
- Text size and accent colour chosen in Control Room now apply here
- New Rotate with the phone switch in Settings

## v0.5.11

- Loading screens now say what they are doing instead of showing a bare spinner
- Wording tidied so no sentence needs a dash

## v0.5.10

- History tab: new “Send now” button sends the anonymous summary immediately instead of waiting for the weekly slot, with last-sent time shown
- Three new opt-in diagnostics: device details (RAM, storage, display, CPU, connection type), installed Wonderdesk app versions, and per-app crash counts from the last 7 days (counts only, never details)
- Samples now also record free RAM and free storage, so weekly averages include memory pressure and storage headroom
- “What's sent” preview shows the full report exactly as it goes out, including any opt-in sections

## v0.5.9

- New History tab (opt-in): Pulse keeps a two-week battery and app history, sampled every half hour at almost no battery cost. See battery over the last 48 hours, how fast it drains per hour on battery, roughly how long is left, and which apps were on screen most while unplugged
- Optional weekly anonymous summary to the Wonderdesk developer (device model, Android version, drain rate, temperature, top apps by screen time) to help make the apps lighter. “What's sent” shows the exact text first

## v0.5.8

- Lighter on the battery: in the Super look the drifting sparkles settle after a few seconds without a touch (the next tap wakes them), so a screen you're reading no longer redraws 25 times a second

## v0.5.7

- Changing the look in Control Room now restyles this app's widgets straight away, with no refresh or restart

## v0.5.6

- Widgets: choose the title colour in each widget's settings (automatic, white, dark, Aqua blue, Super pink or gold), on top of the background looks

## v0.5.5

- Widgets can now match your look: new Bliss blue and Super neon backgrounds, and “Match my Wonderdesk look” follows whatever Control Room is set to

## v0.5.4

- Fixes from a full round of stress testing on every screen

## v0.5.3

- New Tests tab: check the screen for dead pixels, touch and multi-touch, vibration, speaker and flashlight
- Tap the tab you're already on to glide back to the top of its list
- Switches Android greys out as a “Restricted setting” are now explained: the guide walks you through unlocking them in three clear steps, and keeps showing them until the switch is really on (before, on Android 15 and newer it could hide the only hint)
- Nothing required stays hidden: if Android is blocking something the app needs, a slim banner says what and takes you straight to the switch, and Settings › Permissions and access lists everything with its state

## v0.5.2

- Smoother everywhere: new screens, tabs and Settings ease in instead of snapping, and buttons, tabs, chips and switches give a light tap you can feel (following your phone's touch-feedback setting)
- Android's predictive back: the back gesture shows a live preview of where you're going
- Status widget fits any size, down to a 1×1 square with short labels
- Super look: tap sparkles now match Bliss Player: soft glowing sparks with trails, drifting rainbow colours and a faint aurora, instead of big solid circles

## v0.5.1

- Much faster and smoother: the app is now built optimised (the way Play Store apps are), so screens open quicker, scrolling is fluid and the APK is about a quarter of the size
- Screens and lists no longer decode pictures on the main thread, and skip redrawing parts that didn't change

## v0.5.0

- Status widget settings: choose battery, memory and storage rows, the title, and the background
- Fixed the Apps list crashing on phones that report an app twice
- Works properly in landscape: tabs move to a rail at the side so the content keeps its height, and screens that ran out of room now scroll
- If the app ever crashes it says so next time it opens and offers to send a report (only the error, app version and phone model, with file names and links removed). Turn on automatic sending in Control Room › Wonderdesk
- Lists can no longer crash when two items share a name

## v0.4.0

- New Status widget with battery, memory and storage at a glance
- App list shows crisp, properly sized icons
- If the app ever crashes or freezes, a report goes to Control Room so it can be shared and fixed
- App icon art is larger and fills its tile without being cut off

## v0.3.1

- Usage access now opens a guided sheet that takes you straight to Pulse's switch, explains the “restricted setting” step if Android blocks it, and continues as soon as it's on
- App icon redrawn so nothing is cut off: round icons fill the space, the rest sit on an Aqua tile
- Smoother and lighter on the battery: the window background no longer animates, tap effects sleep until you tap, and nothing ticks while the app is in the background
- Opening the app no longer waits on Control Room to read the suite look

## v0.3.0

- Live graphs for CPU, memory, battery and network
- Deep device details and per-app screen time
- Close background processes
