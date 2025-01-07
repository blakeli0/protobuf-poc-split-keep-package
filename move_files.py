import os
import shutil

def move_files(file_list, source_folder, destination_folder):
  """
  Moves files from one folder to another.

  Args:
    file_list: A list of file names to move.
    source_folder: The folder containing the files.
    destination_folder: The folder to move the files to.
  """

  for file_name in file_list:
    source_path = os.path.join(source_folder, file_name)
    destination_path = os.path.join(destination_folder, file_name)
    shutil.move(source_path, destination_path)

if __name__ == "__main__":
  file_list = os.listdir("/Users/blakeli/code/protobuf-poc-split/protobuf-api/src/main/java/com/google/api/")
  # print(file_list)
  source_folder = "/Users/blakeli/code/protobuf-poc-split-keep-package/src/main/java/com/google/protobuf/"
  destination_folder = "/Users/blakeli/code/protobuf-poc-split-keep-package/protobuf-api/src/main/java/com/google/protobuf/"
  os.makedirs(destination_folder, exist_ok=True)

  move_files(file_list, source_folder, destination_folder)