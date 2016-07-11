import argparse

from delete_old_app_data import delete_old_app_data


if __name__ == "__main__":
    parser = argparse.ArgumentParser(usage='maintainer <function> [<args>]', add_help=False)
    parser.add_argument("function", nargs='?',
                        choices=['delete_old_app_data', 'reclaim_disk'])

    args, sub_args = parser.parse_known_args()
    sub_parser = argparse.ArgumentParser()

    if args.function == "delete_old_app_data":
        sub_parser.add_argument("--dir", required=True, help="Directory to delete")
        sub_parser.add_argument("--max_age", required=True, type=int, help="Delete files older than (in seconds)")
        sub_parser.add_argument("--prefix", help="Delete files that start with prefix")
        sub_parser.add_argument("--suffix", help="Delete files that end with suffix")

        args = sub_parser.parse_args(sub_args)
        delete_old_app_data(args.dir, args.max_age, args.prefix, args.suffix)

    elif args.function == "reclaim_disk":
        # Do something else
        pass

    else:
        parser.print_help()