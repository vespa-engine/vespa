import os
import sys
import unittest
import json
from StringIO import StringIO

import src.main.python.parse as parse

class KaggleRawDataParserTest(unittest.TestCase):

    raw_test_file = os.path.dirname(os.path.dirname(os.path.realpath(__file__))) + "/resources/trainPostsSampleWith3Elements.json"
    saved_stdout = sys.stdout
    out = StringIO()


    def setUp(self):
        sys.argv.append(self.raw_test_file)

        self.out = StringIO()
        sys.stdout = self.out

    def tearDown(self):
        sys.argv = [sys.argv[0]]

        sys.stdout = self.saved_stdout


    def test_no_flags(self):
        parser = parse.KaggleRawDataParser()

        self.assertFalse(parser.popularity)
        self.assertEqual(parser.raw_data_file, self.raw_test_file)

    def test_popularity_flag(self):
        sys.argv.append("-p")
        parser = parse.KaggleRawDataParser()

        self.assertTrue(parser.popularity)

    def test_parsing_without_popularity(self):
        parser = parse.KaggleRawDataParser()

        parser.parse()

        output_array = self.out.getvalue().strip().split('\n')
        compare_with = [{
            "fields": {
                "author": "5",
                "blog": "4",
                "blogname": "Matt on Not-WordPress",
                "categories": [
                    "Moblog"
                ],
                "content": "<a href=\"http://matt.files.wordpress.com/2012/03/photo19.jpg\"><img src=\"http://matt.files.wordpress.com/2012/03/photo19.jpg\" alt=\"\" title=\"photo19\" width=\"1000\" height=\"750\" class=\"alignnone size-full wp-image-3838\" /></a>",
                "date": 20120328,
                "date_gmt": "2012-03-28 03:36:57",
                "language": "en",
                "post_id": "507823",
                "tags": [],
                "title": "#vipworkshop dinner",
                "url": "http://matt.wordpress.com/?p=3837"
            },
            "put": "id:blog-search:blog_post::507823"
        },
        {
            "fields": {
                "author": "5",
                "blog": "4",
                "blogname": "Matt on Not-WordPress",
                "categories": [
                    "Moblog"
                ],
                "content": "<a href=\"http://matt.files.wordpress.com/2012/03/photo20.jpg\"><img src=\"http://matt.files.wordpress.com/2012/03/photo20.jpg\" alt=\"\" title=\"photo20\" width=\"1000\" height=\"750\" class=\"alignnone size-full wp-image-3840\" /></a>",
                "date": 20120328,
                "date_gmt": "2012-03-28 04:41:37",
                "language": "en",
                "post_id": "1406963",
                "tags": [],
                "title": "Oven roasted tomatoes",
                "url": "http://matt.wordpress.com/?p=3839"
            },
            "put": "id:blog-search:blog_post::1406963"
        },
        {
            "fields": {
                "author": "5",
                "blog": "4",
                "blogname": "Matt on Not-WordPress",
                "categories": [
                    "Moblog"
                ],
                "content": "<a href=\"http://matt.files.wordpress.com/2012/03/photo21.jpg\"><img src=\"http://matt.files.wordpress.com/2012/03/photo21.jpg\" alt=\"\" title=\"photo21\" width=\"1000\" height=\"750\" class=\"alignnone size-full wp-image-3842\" /></a>",
                "date": 20120328,
                "date_gmt": "2012-03-28 19:59:45",
                "language": "en",
                "post_id": "1329369",
                "tags": [],
                "title": "Fish tacos and spicy slaw",
                "url": "http://matt.wordpress.com/?p=3841"
            },
            "put": "id:blog-search:blog_post::1329369"
        }]

        for i in range(0, 3):
            self.assertEqual(json.loads(output_array[i]), compare_with[i])

    def test_parsing_with_popularity(self):
        sys.argv.append("-p")
        parser = parse.KaggleRawDataParser()

        parser.main()

        output_array = self.out.getvalue().strip().split('\n')
        compare_with = [{
            "fields": {
                "author": "5",
                "blog": "4",
                "blogname": "Matt on Not-WordPress",
                "categories": [
                    "Moblog"
                ],
                "content": "<a href=\"http://matt.files.wordpress.com/2012/03/photo19.jpg\"><img src=\"http://matt.files.wordpress.com/2012/03/photo19.jpg\" alt=\"\" title=\"photo19\" width=\"1000\" height=\"750\" class=\"alignnone size-full wp-image-3838\" /></a>",
                "date": 20120328,
                "date_gmt": "2012-03-28 03:36:57",
                "language": "en",
                "popularity": 1.0,
                "post_id": "507823",
                "tags": [],
                "title": "#vipworkshop dinner",
                "url": "http://matt.wordpress.com/?p=3837"
            },
            "put": "id:blog-search:blog_post::507823"
        },
        {
            "fields": {
                "author": "5",
                "blog": "4",
                "blogname": "Matt on Not-WordPress",
                "categories": [
                    "Moblog"
                ],
                "content": "<a href=\"http://matt.files.wordpress.com/2012/03/photo20.jpg\"><img src=\"http://matt.files.wordpress.com/2012/03/photo20.jpg\" alt=\"\" title=\"photo20\" width=\"1000\" height=\"750\" class=\"alignnone size-full wp-image-3840\" /></a>",
                "date": 20120328,
                "date_gmt": "2012-03-28 04:41:37",
                "language": "en",
                "popularity": 1.0,
                "post_id": "1406963",
                "tags": [],
                "title": "Oven roasted tomatoes",
                "url": "http://matt.wordpress.com/?p=3839"
            },
            "put": "id:blog-search:blog_post::1406963"
        },
        {
            "fields": {
                "author": "5",
                "blog": "4",
                "blogname": "Matt on Not-WordPress",
                "categories": [
                    "Moblog"
                ],
                "content": "<a href=\"http://matt.files.wordpress.com/2012/03/photo21.jpg\"><img src=\"http://matt.files.wordpress.com/2012/03/photo21.jpg\" alt=\"\" title=\"photo21\" width=\"1000\" height=\"750\" class=\"alignnone size-full wp-image-3842\" /></a>",
                "date": 20120328,
                "date_gmt": "2012-03-28 19:59:45",
                "language": "en",
                "popularity": 1.0,
                "post_id": "1329369",
                "tags": [],
                "title": "Fish tacos and spicy slaw",
                "url": "http://matt.wordpress.com/?p=3841"
            },
            "put": "id:blog-search:blog_post::1329369"
        }]

        for i in range(0, 3):
            self.assertEqual(json.loads(output_array[i]), compare_with[i])

if __name__ == '__main__':
    unittest.main()
