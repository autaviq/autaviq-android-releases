# Phantom Reading Prototype 0.2

Standalone Android proof of the child-reading product mechanic. It is not connected to AUTAVIQ or Vercel.

## What this build tests

**Known passage → child reads aloud → words are matched tolerantly → visual events fire during the sentence → passage completes → next passage appears.**

The matcher uses content-word coverage plus approximate reading order. It does not require an exact transcript and does not grade pronunciation.

## Story

1. The little dragon woke up inside his cave.
2. He stretched his wings and stepped outside.
3. A bright butterfly fluttered through the trees.
4. The dragon followed it into the forest.
5. He found a sparkling blue stream.
6. He took a little drink and splashed the water.
7. Then the dragon looked up and saw his home in the distance.
8. He flapped his wings and flew happily home.

Visual events occur at meaningful cue points such as **woke up**, **stretched his wings**, **butterfly**, **stream**, **splashed the water**, and **flew home**.

Android on-device speech recognition is preferred where available; Android's system recogniser is the fallback. Manual Back/Next/Restart controls remain available for demo reliability.

Long-press **Phantom Reading Lab** to reveal debug information.
