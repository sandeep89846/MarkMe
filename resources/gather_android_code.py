import os

# Files/extensions to capture
FILE_EXTENSIONS = ('.kt', '.sq', '.gradle', '.kts', '.xml', '.properties', '.toml')
# Directories to ignore
IGNORE_DIRS = ('build', '.gradle', '.idea', '.git')
# Output file
OUTPUT_FILE = 'android_code_snapshot.txt'

def gather_code(start_dir, output_file):
    print(f"Starting code gathering from {start_dir}...")
    snapshot_content = ""
    
    for root, dirs, files in os.walk(start_dir, topdown=True):
        # Filter out ignored directories
        dirs[:] = [d for d in dirs if d not in IGNORE_DIRS]
        
        for file in files:
            if file.endswith(FILE_EXTENSIONS):
                file_path = os.path.join(root, file)
                # Use a normalized path for the header
                relative_path = os.path.relpath(file_path, start_dir).replace('\\', '/')
                
                separator = f"\n\n--- FILE: {relative_path} ---\n\n"
                snapshot_content += separator
                
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        snapshot_content += f.read()
                    print(f"Added: {relative_path}")
                except Exception as e:
                    snapshot_content += f"!!! ERROR READING FILE: {e} !!!\n"
                    print(f"ERROR reading {relative_path}: {e}")

    with open(output_file, 'w', encoding='utf-8') as f:
        f.write(snapshot_content)
    
    print(f"\nDone! Android code snapshot saved to: {output_file}")

if __name__ == "__main__":
    gather_code('./android-app', OUTPUT_FILE)