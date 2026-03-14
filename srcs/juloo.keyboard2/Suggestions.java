package juloo.keyboard2;

import java.util.List;
import java.util.Arrays;

/** Keep track of the word being typed and provide suggestions for
    [CandidatesView]. */
public final class Suggestions
{
  Callback _callback;

  public Suggestions(Callback c)
  {
    _callback = c;
  }

  public List<String> getDictionary() {
      if (dictionary == null) load_dictionary();
      return dictionary;
  }

  private List<String> dictionary = null;

  public void currently_typed_word(String word)
  {
    if (word.equals(""))
    {
      _callback.set_suggestions(NO_SUGGESTIONS);
      return;
    }

    if (dictionary == null) {
        load_dictionary();
    }

    List<String> matches = new java.util.ArrayList<>();
    String lowerWord = word.toLowerCase();
    
    // Prefix matching from our word list
    if (dictionary != null) {
        for (String dictWord : dictionary) {
            if (dictWord.toLowerCase().startsWith(lowerWord)) {
                matches.add(dictWord);
                if (matches.size() >= 10) break;
            }
        }
    }

    // High priority: exact match or learned word
    if (!matches.contains(word) && !word.trim().isEmpty()) {
        matches.add(0, word);
    }
    
    _callback.set_suggestions(matches);
  }

  private void load_dictionary() {
      try {
          dictionary = new java.util.ArrayList<>();
          
          // Load learned words from internal storage
          java.io.File learnedFile = new java.io.File(Config.globalConfig().getContext().getFilesDir(), "user_dictionary.txt");
          if (learnedFile.exists()) {
              java.io.BufferedReader learnedReader = new java.io.BufferedReader(new java.io.FileReader(learnedFile));
              String line;
              while ((line = learnedReader.readLine()) != null) {
                  String word = line.trim().toLowerCase();
                  if (!word.isEmpty() && !dictionary.contains(word)) {
                      dictionary.add(word);
                  }
              }
              learnedReader.close();
          }

          java.io.InputStream is = null;
          try {
              is = Config.globalConfig().getContext().getAssets().open("dictionary.txt");
          } catch (Exception e) {
              is = Config.globalConfig().getContext().getAssets().open("dictionaries/english.txt");
          }
          java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(is));
          String line;
          while ((line = reader.readLine()) != null) {
              String word = line.split(" ")[0].toLowerCase();
              if (word.length() >= 1) { // Trigger suggestions even for 1-letter words
                  dictionary.add(word);
              }
              if (dictionary.size() > 500000) break; // Increased limit for better coverage
          }
          reader.close();
      } catch (Exception e) {
          android.util.Log.e("Suggestions", "Failed to load dictionary", e);
      }
  }

  public static interface Callback
  {
    public void set_suggestions(List<String> suggestions);
  }

  static final List<String> NO_SUGGESTIONS = Arrays.asList();

  public void commit_word(String word) {
      if (dictionary == null) load_dictionary();
      String lower = word.toLowerCase();
      if (lower.length() < 2) return;
      
      if (dictionary != null) {
          // If word already exists, move it to the top (frequency ranking)
          dictionary.remove(lower);
          dictionary.add(0, lower);
          
          // Save to persistent storage
          save_learned_word(lower);
          
          // Limit size to prevent memory issues
          if (dictionary.size() > 150000) dictionary.remove(dictionary.size() - 1);
      }
  }

  private void save_learned_word(String word) {
      try {
          java.io.File learnedFile = new java.io.File(Config.globalConfig().getContext().getFilesDir(), "user_dictionary.txt");
          java.util.List<String> userWords = new java.util.ArrayList<>();
          if (learnedFile.exists()) {
              java.util.Scanner s = new java.util.Scanner(learnedFile);
              while (s.hasNextLine()) userWords.add(s.nextLine());
              s.close();
          }
          userWords.remove(word);
          userWords.add(0, word);
          if (userWords.size() > 5000) userWords.remove(userWords.size() - 1);
          
          java.io.FileWriter writer = new java.io.FileWriter(learnedFile);
          for (String w : userWords) writer.write(w + "\n");
          writer.close();
      } catch (Exception e) {
          android.util.Log.e("Suggestions", "Failed to save learned word", e);
      }
  }
}
