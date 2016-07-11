import os
import time


def delete_old_app_data(path, max_age, prefix, suffix):
    deleted = 0
    total = 0
    for file in os.listdir(path):
        total += 1
        if (not prefix or file.startswith(prefix)) and (not suffix or file.endswith(suffix)):
            if os.path.getmtime(os.path.join(path, file)) + max_age < time.time():
                os.remove(os.path.join(path, file))
                deleted += 1

    print("Deleted " + str(deleted) + " out of " + str(total))
