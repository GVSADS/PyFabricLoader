import os
import sys
import json
import zipfile
import tempfile
import shutil
import argparse

# 支持的Minecraft版本
MINECRAFT_VERSIONS = [
    "1.18.1", "1.18.2", "1.19.0", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
    "1.20.0", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
    "1.21.0", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6",
    "1.21.7", "1.21.8", "1.21.9", "1.21.10"
]

def modify_jar_for_version(input_jar, output_dir, mc_version):
    """为特定Minecraft版本修改jar文件"""
    # 构建输出文件名
    base_name = os.path.basename(input_jar)
    name_parts = base_name.split('.')
    output_name = f"{name_parts[0]}-{mc_version}.{'_'.join(name_parts[1:])}"
    output_jar = os.path.join(output_dir, output_name)
    
    # 读取原始fabric.mod.json
    original_json = os.path.join(os.path.dirname(os.path.abspath(__file__)), 
                               "src/main/resources/fabric.mod.json")
    
    if not os.path.exists(original_json):
        print(f"Error: Could not find fabric.mod.json at {original_json}")
        return False
    
    try:
        # 读取并修改fabric.mod.json
        with open(original_json, 'r', encoding='utf-8') as f:
            mod_data = json.load(f)
        
        # 修改版本约束
        mod_data['depends']['minecraft'] = f'[{mc_version}]'
        
        # 创建临时目录
        temp_dir = tempfile.mkdtemp()
        
        try:
            # 解压jar文件
            if not os.path.exists(input_jar):
                print(f"Error: Input jar not found: {input_jar}")
                return False
                
            with zipfile.ZipFile(input_jar, 'r') as zip_ref:
                zip_ref.extractall(temp_dir)
            
            # 写入修改后的fabric.mod.json
            meta_inf_dir = os.path.join(temp_dir, 'META-INF')
            os.makedirs(meta_inf_dir, exist_ok=True)
            
            mod_json_path = os.path.join(meta_inf_dir, 'fabric.mod.json')
            with open(mod_json_path, 'w', encoding='utf-8') as f:
                json.dump(mod_data, f, indent=2)
            
            # 创建输出jar文件
            os.makedirs(output_dir, exist_ok=True)
            with zipfile.ZipFile(output_jar, 'w', zipfile.ZIP_DEFLATED) as zip_out:
                for root, dirs, files in os.walk(temp_dir):
                    for file in files:
                        file_path = os.path.join(root, file)
                        arcname = os.path.relpath(file_path, temp_dir).replace('\\', '/')
                        zip_out.write(file_path, arcname)
            
            print(f"Successfully created: {output_jar}")
            return True
            
        finally:
            # 清理临时目录
            shutil.rmtree(temp_dir, ignore_errors=True)
            
    except Exception as e:
        print(f"Error processing version {mc_version}: {e}")
        return False

def main():
    parser = argparse.ArgumentParser(description="Create version-specific JAR files for Minecraft mods")
    parser.add_argument("--input-jar", required=True, help="Path to the input JAR file")
    parser.add_argument("--output-dir", default="./build/libs", help="Output directory for version-specific JARs")
    parser.add_argument("--version", help="Specific Minecraft version to target (optional)")
    parser.add_argument("--all", action="store_true", help="Generate JARs for all supported versions")
    
    args = parser.parse_args()
    
    # 确保输出目录存在
    os.makedirs(args.output_dir, exist_ok=True)
    
    # 确定要处理的版本列表
    versions_to_process = []
    if args.version:
        versions_to_process = [args.version]
    elif args.all:
        versions_to_process = MINECRAFT_VERSIONS
    else:
        print("Error: You must specify either --version or --all")
        parser.print_help()
        return 1
    
    # 处理每个版本
    success_count = 0
    for version in versions_to_process:
        if modify_jar_for_version(args.input_jar, args.output_dir, version):
            success_count += 1
    
    print(f"\nProcessing complete. Successfully created {success_count} of {len(versions_to_process)} JAR files.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
