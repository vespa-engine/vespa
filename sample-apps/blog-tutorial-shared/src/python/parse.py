# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
import json
import argparse


class KaggleRawDataParser:

    popularity = False
    raw_data_file = None
    total_number_of_likes = 0
    likes_per_blog = {}

    def __init__(self):
        parser = argparse.ArgumentParser()
        parser.add_argument("-p", "--popularity", action="store_true", help="add 'popularity' field")
        parser.add_argument("file", help="location of file to be parsed")
        args = parser.parse_args()

        self.popularity = args.popularity
        self.raw_data_file = args.file

    def main(self):
        if self.popularity:
            self.calculate_popularity()
        self.parse()

    def calculate_popularity(self):
        unparsed_file = open(self.raw_data_file, "r")

        for line in unparsed_file:
            data = json.loads(line)

            self.total_number_of_likes += len(data["likes"])
            if data["blog"] in self.likes_per_blog:
                self.likes_per_blog[data["blog"]] += len(data["likes"])
            else:
                self.likes_per_blog[data["blog"]] = len(data["likes"])

        unparsed_file.close()

    def parse(self):
        unparsed_file = open(self.raw_data_file, "r")

        for line in unparsed_file:
            data = json.loads(line)

            parsed_data = {
                "put": "id:blog-search:blog_post::" + data["post_id"],
                "fields": {
                    "blogname": data["blogname"],
                    "post_id": data["post_id"],
                    "author": data["author"],
                    "language": data["language"],
                    "categories": data["categories"],
                    "title": data["title"],
                    "blog": data["blog"],
                    "date_gmt": data["date_gmt"],
                    "url": data["url"],
                    "content": data["content"],
                    "tags": data["tags"],
                    "date": int(data["date_gmt"][0:4] + data["date_gmt"][5:7] + data["date_gmt"][8:10])
                }
            }
            if self.popularity:
                parsed_data["fields"]["popularity"] = \
                    float(self.likes_per_blog[data["blog"]]) / float(self.total_number_of_likes)

            print(json.dumps(parsed_data))

        unparsed_file.close()

if __name__ == '__main__':
    KaggleRawDataParser().main()
